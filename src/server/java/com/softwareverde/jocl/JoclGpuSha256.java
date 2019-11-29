package com.softwareverde.jocl;

import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.miner.GpuSha256;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;
import org.jocl.*;

import static org.jocl.CL.*;
// import static org.jocl.Jocl.*;

public class JoclGpuSha256 implements GpuSha256 {
    protected static final Object _mutex = new Object();
    protected static JoclGpuSha256 _instance;
    public static GpuSha256 getInstance() {
        if (_instance == null) {
            synchronized (_mutex) {
                if (_instance == null) {
                    _instance = new JoclGpuSha256();
                }
            }
        }

        return _instance;
    }

    public static void shutdown() {
        synchronized (_mutex) {
            if (_instance != null) {
                _instance._shutdown();
            }
            _instance = null;
        }
    }

    protected static final int defaultPlatformIndex = 0;
    protected static final long defaultDeviceType = (~CL_DEVICE_TYPE_CPU); // CL_DEVICE_TYPE_GPU; // CL_DEVICE_TYPE_ALL;

    protected static final String kernelName = "sha256_crypt_kernel";
    protected static final String programSource = IoUtil.getResource("/kernels/sha256_crypt_kernel.cl");
    protected static final int SHA256_BYTE_COUNT = 32;

    protected static final int initialReadBufferByteCount = 1024;
    public static final int maxBatchSize = (1024 * 256);

    protected cl_context _context;
    protected cl_kernel _kernel;
    protected cl_command_queue[] _commandQueues;

    protected final int[] _metaData = new int[3];
    protected final cl_mem _clMetaDataBuffer;

    protected final int[] _writeBuffer = new int[maxBatchSize * (SHA256_BYTE_COUNT / Sizeof.cl_uint)];
    protected final cl_mem _clWriteBuffer;

    protected byte[] _readBuffer;
    protected cl_mem _clReadBuffer;

    protected boolean _isShutdown = false;

    protected final boolean _initCL() {
        setExceptionsEnabled(true);

        final int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        if (numPlatforms == 0) {
            System.err.println("No OpenCL platforms available");
            return false;
        }

        final cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);

        final cl_platform_id platform = platforms[defaultPlatformIndex];

        final cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        final int devicesCount;
        {
            final int numDevicesArray[] = new int[1];
            clGetDeviceIDs(platform, defaultDeviceType, 0, null, numDevicesArray);
            devicesCount = numDevicesArray[0];
        }

        if (devicesCount == 0) {
            System.err.println("No devices with type " + stringFor_cl_device_type(defaultDeviceType) + " on platform " + defaultPlatformIndex);
            return false;
        }

        final cl_device_id devices[] = new cl_device_id[devicesCount];
        clGetDeviceIDs(platform, defaultDeviceType, devicesCount, devices, null);
        _context = clCreateContext(contextProperties, devicesCount, devices, null, null, null);
        _commandQueues = new cl_command_queue[devicesCount];
        for (int i = 0; i < devicesCount; ++i) {
            final cl_device_id device = devices[i];
            _commandQueues[i] = clCreateCommandQueue(_context, device, 0, null);
        }

        return true;
    }

    protected void _initKernel() {
        final cl_program program = clCreateProgramWithSource(_context, 1, new String[]{ programSource }, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        _kernel = clCreateKernel(program, kernelName, null);
        clReleaseProgram(program);
    }

    protected void _resizeReadBuffer(final int newReadBufferByteCount) {
        { // Logging...
            final int currentSize = (_readBuffer == null ? 0 : _readBuffer.length);
            Logger.debug((newReadBufferByteCount > currentSize ? "Growing" : "Shrinking") + " GpuSha256._readBuffer from " + currentSize + " to " + newReadBufferByteCount + " bytes.");
        }

        _readBuffer = new byte[newReadBufferByteCount];
        _clReadBuffer = clCreateBuffer(_context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, _readBuffer.length, Pointer.to(_readBuffer), null);
        clSetKernelArg(_kernel, 1, Sizeof.cl_mem, Pointer.to(_clReadBuffer));
    }

    protected void _shutdown() {
        if (_kernel != null) {
            clReleaseKernel(_kernel);
        }

        if (_commandQueues != null) {
            for (int i = 0; i < _commandQueues.length; ++i) {
                clReleaseCommandQueue(_commandQueues[i]);
            }
        }

        _isShutdown = true;
    }

    protected JoclGpuSha256() {
        _initCL();
        _initKernel();

        { // Init buffers...
            _clMetaDataBuffer = clCreateBuffer(_context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_uint * _metaData.length, Pointer.to(_metaData), null);
            clSetKernelArg(_kernel, 0, Sizeof.cl_mem, Pointer.to(_clMetaDataBuffer));

            _clWriteBuffer = clCreateBuffer(_context, CL_MEM_WRITE_ONLY, Sizeof.cl_uint * _writeBuffer.length, null, null);
            clSetKernelArg(_kernel, 2, Sizeof.cl_mem, Pointer.to(_clWriteBuffer));
        }

        _resizeReadBuffer(initialReadBufferByteCount);
    }

    protected int _commandQueueIndex = 0;

    @Override
    public Integer getMaxBatchSize() {
        return JoclGpuSha256.maxBatchSize;
    }

    @Override
    public List<Sha256Hash> sha256(final List<? extends ByteArray> inputs) {
        if (_isShutdown) {
            throw new IllegalStateException("GpuSha256 kernel has been shutdown for this instance. Be sure GpuSha256.getInstance() is called for a new reference after a call to SpuSha256.shutdown().");
        }

        final int inputsCount = inputs.getCount();
        if (inputsCount == 0) { return new MutableList<Sha256Hash>(); }
        final int byteCountPerInput;
        {
            final ByteArray byteArray = inputs.get(0);
            if (byteArray == null) {
                Logger.debug("NOTICE: null byte array found in batch at: 0");
                return null;
            }
            byteCountPerInput = byteArray.getByteCount();
        }

        final int commandQueueIndex;
        synchronized (_mutex) {
            commandQueueIndex = _commandQueueIndex;
            _commandQueueIndex = (_commandQueueIndex + 1) % _commandQueues.length;
        }

        final byte[] readBuffer = new byte[byteCountPerInput * inputsCount];
        for (int i = 0; i < inputsCount; ++i) {
            final byte[] bytes;
            {
                final ByteArray byteArray = inputs.get(i);
                if (byteArray == null) {
                    Logger.debug("NOTICE: null byte array found in batch at: " + i);
                    return null;
                }

                if (byteArray.getByteCount() != byteCountPerInput) {
                    Logger.debug("NOTICE: All input hashes must be the same length at: " + i);
                    return null;
                }

                bytes = byteArray.getBytes();
            }
            System.arraycopy(bytes, 0, readBuffer, (i * byteCountPerInput), byteCountPerInput);
        }

        if (_readBuffer.length < readBuffer.length) {
            _resizeReadBuffer(readBuffer.length);
        }

        final int integersPerHash = (SHA256_BYTE_COUNT / Sizeof.cl_uint);

        final int[] metaData = new int[3];
        metaData[0] = integersPerHash;
        metaData[1] = inputsCount;
        metaData[2] = byteCountPerInput;

        final long[] globalWorkSize = new long[] { inputsCount };
        final long[] localWorkSize = new long[] { 1 };

        final int[] writeBuffer = new int[integersPerHash * inputsCount];

        synchronized (_commandQueues[commandQueueIndex]) {
            clFinish(_commandQueues[commandQueueIndex]);

            clEnqueueWriteBuffer(_commandQueues[commandQueueIndex], _clMetaDataBuffer, CL_TRUE, 0, Sizeof.cl_uint * 3, Pointer.to(metaData), 0, null, null);
            clEnqueueWriteBuffer(_commandQueues[commandQueueIndex], _clReadBuffer, CL_TRUE, 0, readBuffer.length, Pointer.to(readBuffer), 0, null, null);
            clEnqueueNDRangeKernel(_commandQueues[commandQueueIndex], _kernel, 1, null, globalWorkSize, localWorkSize, 0, null, null);
            clEnqueueReadBuffer(_commandQueues[commandQueueIndex], _clWriteBuffer, CL_TRUE, 0, Sizeof.cl_uint * writeBuffer.length, Pointer.to(writeBuffer), 0, null, null);
        }

        final ImmutableListBuilder<Sha256Hash> listBuilder = new ImmutableListBuilder<Sha256Hash>(inputsCount);
        for (int i = 0; i < inputsCount; ++i) {
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            for (int j = 0; j < integersPerHash; ++j) {
                byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(writeBuffer[(i * integersPerHash) + j]), Endian.BIG);
            }

            listBuilder.add(MutableSha256Hash.wrap(byteArrayBuilder.build()));
        }
        return listBuilder.build();
    }
}
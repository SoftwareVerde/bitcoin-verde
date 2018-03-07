package com.softwareverde.jocl;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.IoUtil;
import org.jocl.*;

import static org.jocl.CL.*;

public class GpuSha256 {
    protected static final Object _mutex = new Object();
    protected static GpuSha256 _instance;
    public static GpuSha256 getInstance() {
        if (_instance == null) {
            synchronized (_mutex) {
                if (_instance == null) {
                    _instance = new GpuSha256();
                }
            }
        }

        return _instance;
    }

    protected static final int defaultPlatformIndex = 0;
    protected static final long defaultDeviceType = CL_DEVICE_TYPE_GPU; // CL_DEVICE_TYPE_ALL;
    protected static final int defaultDeviceIndex = 0;

    protected static final String programSource = IoUtil.getResource("/kernels/sha256_crypt_kernel.cl");
    protected static final int SHA256_BYTE_COUNT = 64;

    protected static final int _maxBuffSize = 2048;
    public static final int maxBatchSize = 40; // 1024 * 8;

    protected cl_context _context;
    protected cl_command_queue _commandQueue;
    protected cl_kernel _kernel;

    protected final int[] _metaData = new int[3];
    protected final byte[] _readBuffer = new byte[_maxBuffSize * maxBatchSize];
    protected final int[] _writeBuffer = new int[maxBatchSize * (SHA256_BYTE_COUNT / Sizeof.cl_uint)];

    protected final cl_mem _clMetaDataBuffer;
    protected final cl_mem _clReadBuffer;
    protected final cl_mem _clWriteBuffer;

    protected final boolean _initCL(final int platformIndex, final long deviceType, final int deviceIndex) {
        CL.setExceptionsEnabled(true);

        final int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        if (numPlatforms == 0) {
            System.err.println("No OpenCL platforms available");
            return false;
        }

        final cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);

        final cl_platform_id platform = platforms[platformIndex];

        final cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        final int devicesCount;
        {
            final int numDevicesArray[] = new int[1];
            clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
            devicesCount = numDevicesArray[0];
            System.out.println("Device Count: "+ devicesCount);
        }

        if (devicesCount == 0) {
            System.err.println("No devices with type " + CL.stringFor_cl_device_type(deviceType) + " on platform " + platformIndex);
            return false;
        }

        final cl_device_id devices[] = new cl_device_id[devicesCount];
        clGetDeviceIDs(platform, deviceType, devicesCount, devices, null);
        final cl_device_id device = devices[deviceIndex];

        _context = clCreateContext(contextProperties, 1, new cl_device_id[]{ device }, null, null, null);

        _commandQueue = clCreateCommandQueue(_context, device, 0, null);

        return true;
    }

    protected void _initKernel(final String kernelName, final String programSource) {
        final cl_program program = clCreateProgramWithSource(_context, 1, new String[]{ programSource }, null, null);
        clBuildProgram(program, 0, null, null, null, null);

        _kernel = clCreateKernel(program, kernelName, null);
        clReleaseProgram(program);
    }

    protected GpuSha256() {
        _initCL(defaultPlatformIndex, defaultDeviceType, defaultDeviceIndex);
        _initKernel("sha256_crypt_kernel", programSource);

        { // Init buffers...
            _clMetaDataBuffer = clCreateBuffer(_context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_uint * _metaData.length, Pointer.to(_metaData), null);
            _clReadBuffer = clCreateBuffer(_context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, _readBuffer.length, Pointer.to(_readBuffer), null);
            _clWriteBuffer = clCreateBuffer(_context, CL_MEM_WRITE_ONLY, Sizeof.cl_uint * _writeBuffer.length, null, null);

            clSetKernelArg(_kernel, 0, Sizeof.cl_mem, Pointer.to(_clMetaDataBuffer));
            clSetKernelArg(_kernel, 1, Sizeof.cl_mem, Pointer.to(_clReadBuffer));
            clSetKernelArg(_kernel, 2, Sizeof.cl_mem, Pointer.to(_clWriteBuffer));
        }
    }

    public List<Hash> sha256(final List<? extends ByteArray> inputs) {
        final int inputsCount = inputs.getSize();
        if (inputsCount == 0) { return new MutableList<Hash>(); }

        final int inputLength = inputs.get(0).getByteCount();

        final byte[] readBuffer = new byte[inputLength * inputsCount];
        for (int i=0; i<inputsCount; ++i) {
            final byte[] bytes = inputs.get(i).getBytes();
            if (bytes.length != inputLength) { throw new IllegalArgumentException("All input hashes must be the same length."); }

//            for (int j=0; j<inputLength; ++j) {
//                readBuffer[(i*inputLength) + j] = bytes[j];
//            }
            System.arraycopy(bytes, 0, readBuffer, (i * inputLength), inputLength);
        }

        final int integersPerHash = (SHA256_BYTE_COUNT / Sizeof.cl_uint);

        final int[] metaData = new int[3];
        metaData[0] = integersPerHash;
        metaData[1] = inputsCount;
        metaData[2] = inputLength;

        final long[] globalWorkSize = new long[] { inputsCount };
        final long[] localWorkSize = new long[] { 1 };

        final int[] writeBuffer = new int[integersPerHash * inputsCount];

        // synchronized (_mutex) {
            clEnqueueWriteBuffer(_commandQueue, _clMetaDataBuffer, CL_TRUE, 0, Sizeof.cl_uint * 3, Pointer.to(metaData), 0, null, null);
            clEnqueueWriteBuffer(_commandQueue, _clReadBuffer, CL_TRUE, 0, readBuffer.length, Pointer.to(readBuffer), 0, null, null);
            clEnqueueNDRangeKernel(_commandQueue, _kernel, 1, null, globalWorkSize, localWorkSize, 0, null, null);
            clEnqueueReadBuffer(_commandQueue, _clWriteBuffer, CL_TRUE, 0, Sizeof.cl_uint * writeBuffer.length, Pointer.to(writeBuffer), 0, null, null);

            clFinish(_commandQueue);
        // }

        final MutableList<Hash> hashes = new MutableList<Hash>();
        for (int i=0; i<inputsCount; ++i) {
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            for (int j = 0; j < integersPerHash; ++j) {
                byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(writeBuffer[(i * integersPerHash) + j]), Endian.BIG);
            }

            hashes.add(new ImmutableHash(byteArrayBuilder.build()));
        }

        return hashes;
    }

    public void shutdown() {
        if (_kernel != null) {
            clReleaseKernel(_kernel);
        }

        if (_commandQueue != null) {
            clReleaseCommandQueue(_commandQueue);
        }
    }
}
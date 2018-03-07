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
    protected static final int defaultPlatformIndex = 0;
    protected static final long defaultDeviceType = CL_DEVICE_TYPE_GPU; // CL_DEVICE_TYPE_ALL;
    protected static final int defaultDeviceIndex = 0;

    protected static final String programSource = IoUtil.getResource("/kernels/sha256_crypt_kernel.cl");
    protected static final int SHA256_BYTE_COUNT = 64;

    protected cl_context _context;
    protected cl_command_queue _commandQueue;
    protected cl_kernel _kernel;

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

    protected final void _initKernel(final String kernelName, final String programSource) {
        final cl_program program = clCreateProgramWithSource(_context, 1, new String[]{ programSource }, null, null);
        clBuildProgram(program, 0, null, null, null, null);

        _kernel = clCreateKernel(program, kernelName, null);
        clReleaseProgram(program);
    }

    public GpuSha256() {
        _initCL(defaultPlatformIndex, defaultDeviceType, defaultDeviceIndex);
        _initKernel("sha256_crypt_kernel", programSource);
    }

    public List<Hash> sha256(final List<?  extends ByteArray> inputs) {
        final int inputsCount = inputs.getSize();
        if (inputsCount == 0) { return new MutableList<Hash>(); }

        final int inputLength = inputs.get(0).getByteCount();
        final int GLOBAL_WORK_SIZE = inputsCount;

        final byte[] saved_plain = new byte[inputLength * inputsCount];
        for (int i=0; i<inputsCount; ++i) {
            final byte[] bytes = inputs.get(i).getBytes();
            if (bytes.length != inputLength) { throw new IllegalArgumentException("All input hashes must be the same length."); }

            for (int j=0; j<inputLength; ++j) {
                saved_plain[(i*inputLength) + j] = bytes[j];
            }
        }

        final int INTEGERS_PER_HASH = (SHA256_BYTE_COUNT / Sizeof.cl_uint);

        final int[] datai = new int[3];
        datai[0] = INTEGERS_PER_HASH;
        datai[1] = GLOBAL_WORK_SIZE;
        datai[2] = inputLength;

        final long[] global_work_size = new long[] { GLOBAL_WORK_SIZE };
        final long[] local_work_size = new long[] { 1 };

        final int[] partial_hashes = new int[INTEGERS_PER_HASH * GLOBAL_WORK_SIZE];

        final cl_mem data_info = clCreateBuffer(_context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_uint * datai.length, Pointer.to(datai), null);
        final cl_mem buffer_keys = clCreateBuffer(_context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, saved_plain.length, Pointer.to(saved_plain), null);
        final cl_mem buffer_out = clCreateBuffer(_context, CL_MEM_WRITE_ONLY, Sizeof.cl_uint * partial_hashes.length, null, null);

        clSetKernelArg(_kernel, 0, Sizeof.cl_mem, Pointer.to(data_info));
        clSetKernelArg(_kernel, 1, Sizeof.cl_mem, Pointer.to(buffer_keys));
        clSetKernelArg(_kernel, 2, Sizeof.cl_mem, Pointer.to(buffer_out));

        clEnqueueNDRangeKernel(_commandQueue, _kernel, 1, null, global_work_size, local_work_size, 0, null, null);
        clEnqueueReadBuffer(_commandQueue, buffer_out, CL_TRUE, 0, Sizeof.cl_uint * partial_hashes.length, Pointer.to(partial_hashes), 0, null, null);

        // clEnqueueWriteBuffer(command_queue, data_info, CL_TRUE, 0, Sizeof.cl_uint * 3, Pointer.to(datai), 0, null, null);
        // clEnqueueWriteBuffer(command_queue, buffer_keys, CL_TRUE, 0, SHA256_PLAINTEXT_LENGTH * KPC, Pointer.to(saved_plain), 0, null, null);

        // clFinish(_commandQueue);

        final MutableList<Hash> hashes = new MutableList<Hash>();
        for (int i=0; i<inputsCount; ++i) {
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            for (int j = 0; j < INTEGERS_PER_HASH; ++j) {
                byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(partial_hashes[(i * INTEGERS_PER_HASH) + j]), Endian.BIG);
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
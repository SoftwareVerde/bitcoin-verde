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

import java.security.InvalidParameterException;

import static org.jocl.CL.*;

/**
 * A basic vector addition test
 */
public class GpuSha256 {
    // The default platform, device type and device index
    protected static final int defaultPlatformIndex = 0;
    protected static final long defaultDeviceType = CL_DEVICE_TYPE_ALL;
    protected static final int defaultDeviceIndex = 0;

    protected cl_context context;
    protected cl_command_queue command_queue;
    protected cl_kernel kernel;

    public GpuSha256() {
        _init();
    }

    /**
     * Default initialization of the context and the command queue
     *
     * @param platformIndex The platform to use
     * @param deviceType The device type
     * @param deviceIndex The device index
     * @return Whether the initialization was successful
     */
    protected final boolean _initCL(int platformIndex, long deviceType, int deviceIndex) {
        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        if (numPlatforms == 0) {
            System.err.println("No OpenCL platforms available");
            return false;
        }

        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        if (numDevices == 0) {
            System.err.println("No devices with type " + CL.stringFor_cl_device_type(deviceType) + " on platform "+platformIndex);
            return false;
        }

        // Obtain a device ID
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        context = clCreateContext(contextProperties, 1, new cl_device_id[]{ device }, null, null, null);

        // Create a command-queue for the selected device
        command_queue = clCreateCommandQueue(context, device, 0, null);
        // command_queue = clCreateCommandQueueWithProperties(context, device, null, null); // Not supported.

        return true;
    }

    /**
     * Initialize the kernel with the given name and source
     *
     * @param kernelName The kernel name
     * @param programSource The program source
     */
    protected final void _initKernel(final String kernelName, final String programSource) {
        cl_program program = clCreateProgramWithSource(context, 1, new String[]{ programSource }, null, null);   // Create the program from the source code
        clBuildProgram(program, 0, null, null, null, null);                              // Build the program
        kernel = clCreateKernel(program, kernelName, null);                                                                     // Create the kernel
        clReleaseProgram(program);
    }

    protected void _init() {
        _initCL(defaultPlatformIndex, defaultDeviceType, defaultDeviceIndex);
        _initKernel("sha256_crypt_kernel", programSource);
    }

    /**
     * The source code of the OpenCL program to execute
     */
    private static String programSource = IoUtil.getResource("/kernels/sha256_crypt_kernel.cl");

    private static final int SHA256_BYTE_COUNT = 64;

    public void shutdown() {
        if (kernel != null) {
            clReleaseKernel(kernel);
        }

        if (command_queue != null) {
            clReleaseCommandQueue(command_queue);
        }
    }

    public List<Hash> sha256(final List<byte[]> inputs) {
        final int inputsCount = inputs.getSize();
        if (inputsCount == 0) { return new MutableList<Hash>(); }

        final int inputLength = inputs.get(0).length;
        final int GLOBAL_WORK_SIZE = inputsCount;

        final byte[] saved_plain = new byte[inputLength * inputsCount];
        for (int i=0; i<inputsCount; ++i) {
            final byte[] bytes = inputs.get(i);
            if (bytes.length != inputLength) { throw new InvalidParameterException("All input hashes must be the same length."); }

            for (int j=0; j<inputLength; ++j) {
                saved_plain[(i*inputLength) + j] = bytes[j];
            }
        }

        final int INTEGERS_PER_HASH = (SHA256_BYTE_COUNT / Sizeof.cl_uint);

        final int[] datai = new int[3];
        datai[0] = INTEGERS_PER_HASH;
        datai[1] = GLOBAL_WORK_SIZE;
        datai[2] = inputLength;

        long[] global_work_size = new long[] { GLOBAL_WORK_SIZE };
        long[] local_work_size = new long[] { 1 };

        int[] partial_hashes = new int[INTEGERS_PER_HASH * GLOBAL_WORK_SIZE];

        // Allocate the memory objects for the input- and output data
        // cl_mem buffer_keys = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, (SHA256_PLAINTEXT_LENGTH * KPC), Pointer.to(saved_plain), null);
        cl_mem buffer_keys = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, saved_plain.length, Pointer.to(saved_plain), null);
        cl_mem buffer_out = clCreateBuffer(context, CL_MEM_WRITE_ONLY, Sizeof.cl_uint * partial_hashes.length, null, null);
        cl_mem data_info = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_uint * datai.length, Pointer.to(datai), null);

        // Set the arguments for the kernel
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(data_info));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(buffer_keys));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(buffer_out));

        // Set the values?
        // clEnqueueWriteBuffer(command_queue, data_info, CL_TRUE, 0, Sizeof.cl_uint * 3, Pointer.to(datai), 0, null, null);
        // clEnqueueWriteBuffer(command_queue, buffer_keys, CL_TRUE, 0, SHA256_PLAINTEXT_LENGTH * KPC, Pointer.to(saved_plain), 0, null, null);

        // clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[] {n}, null, 0, null, null);
        clEnqueueNDRangeKernel(command_queue, kernel, 1, null, global_work_size, local_work_size, 0, null, null);

        // clFinish(command_queue);

        // Read the output data
        clEnqueueReadBuffer(command_queue, buffer_out, CL_TRUE, 0, Sizeof.cl_uint * partial_hashes.length, Pointer.to(partial_hashes), 0, null, null);

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

}
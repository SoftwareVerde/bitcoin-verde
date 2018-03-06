package com.softwareverde.jocl;

import static org.jocl.CL.*;
import org.jocl.*;

/**
 * A basic vector addition test
 */
public class GpuSha256 {
    // The default platform, device type and device index
    protected static final int defaultPlatformIndex = 0;
    protected static final long defaultDeviceType = CL_DEVICE_TYPE_ALL;
    protected static final int defaultDeviceIndex = 0;

    protected cl_context context;
    protected cl_command_queue commandQueue;
    protected cl_kernel kernel;

    /**
     * Default initialization of the context and the command queue
     *
     * @param platformIndex The platform to use
     * @param deviceType The device type
     * @param deviceIndex The device index
     * @return Whether the initialization was successful
     */
    protected final boolean initCL(int platformIndex, long deviceType, int deviceIndex) {
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
        context = clCreateContext(contextProperties, 1, new cl_device_id[]{device}, null, null, null);

        // Create a command-queue for the selected device
        commandQueue = clCreateCommandQueue(context, device, 0, null);

        return true;
    }

    /**
     * Initialize the kernel with the given name and source
     *
     * @param kernelName The kernel name
     * @param programSource The program source
     */
    protected final void initKernel(String kernelName, String programSource) {
        cl_program program = clCreateProgramWithSource(context, 1, new String[]{ programSource }, null, null);   // Create the program from the source code
        clBuildProgram(program, 0, null, null, null, null);                              // Build the program
        kernel = clCreateKernel(program, kernelName, null);                                                                     // Create the kernel
        clReleaseProgram(program);
    }

    /**
     * Release the kernel that has been created in
     * {@link #initKernel(String, String)}
     */
    protected final void shutdownKernel() {
        if (kernel != null) {
            clReleaseKernel(kernel);
        }
    }


    protected final void shutdownCL() {
        if (commandQueue != null) {
            clReleaseCommandQueue(commandQueue);
        }
    }

    /**
     * The source code of the OpenCL program to execute
     */
    private static String programSource =
            "__kernel void "+
                    "sampleKernel(__global const float *a,"+
                    "             __global const float *b,"+
                    "             __global float *c)"+
                    "{"+
                    "    int gid = get_global_id(0);"+
                    "    c[gid] = a[gid] + b[gid];"+
                    "}";

    public void basicTest() {
        initCL(defaultPlatformIndex, defaultDeviceType, defaultDeviceIndex);
        initKernel("sampleKernel", programSource);

        // Create input- and output data
        int n = 10;
        float srcArrayA[] = new float[n];
        float srcArrayB[] = new float[n];
        float dstArray[] = new float[n];
        for (int i=0; i<n; i++) {
            srcArrayA[i] = i;
            srcArrayB[i] = i;
        }

        // Allocate the memory objects for the input- and output data
        cl_mem srcMemA = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float * n, Pointer.to(srcArrayA), null);
        cl_mem srcMemB = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float * n, Pointer.to(srcArrayB), null);
        cl_mem dstMem = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_float * n, null, null);

        // Set the arguments for the kernel
        int a = 0;
        clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemA));
        clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemB));
        clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(dstMem));

        // Execute the kernel
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[] {n}, null, 0, null, null);

        // Read the output data
        clEnqueueReadBuffer(commandQueue, dstMem, CL_TRUE, 0, n * Sizeof.cl_float, Pointer.to(dstArray), 0, null, null);

        // Compute the reference result
        float expected[] = new float[n];
        for (int i=0; i<n; i++) {
            expected[i] = srcArrayA[i] + srcArrayB[i];
        }

        boolean passed = epsilonEqual(expected, dstArray);

        shutdownKernel();
        shutdownCL();

        System.out.println(passed);
    }


    /**
     * Returns whether the given arrays are equal up to a small epsilon
     *
     * @param expected The expected results
     * @param actual The actual results
     * @return Whether the arrays are epsilon-equal
     */
    private static boolean epsilonEqual(float expected[], float actual[]) {
        final float epsilon = 1e-7f;
        if (expected.length != actual.length) {
            return false;
        }
        for (int i=0; i<expected.length; i++) {
            float x = expected[i];
            float y = actual[i];
            boolean epsilonEqual = Math.abs(x - y) <= epsilon * Math.abs(x);
            if (! epsilonEqual) {
                return false;
            }
        }
        return true;
    }

}
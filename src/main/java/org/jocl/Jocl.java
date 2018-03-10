/*package org.jocl;

public class Jocl {
    public static final int CL_DEVICE_TYPE_CPU = 0;
    public static final int CL_CONTEXT_PLATFORM = 0;
    public static final int CL_MEM_READ_ONLY = 1;
    public static final int CL_MEM_COPY_HOST_PTR = 2;
    public static final int CL_MEM_WRITE_ONLY = 3;
    public static final int CL_TRUE = 1;

    public static class Sizeof {
        public static int cl_uint = 0;
        public static int cl_mem = 0;
    }

    public static class CL {
        public static void setExceptionsEnabled(final boolean b) { }
        public static String stringFor_cl_device_type(final Object o) { return ""; }
    }


    public static class Pointer {
        public static Pointer to(final Object o) { return new Pointer(); }
    }

    public static class cl_context { }
    public static class cl_kernel { }
    public static class cl_command_queue { }
    public static class cl_mem { }
    public static class cl_device_id { }
    public static class cl_program { }
    public static class cl_platform_id { }

    public static void clGetPlatformIDs(final int i, final Object o, final int[] numPlatformsArray) { }

    public static class cl_context_properties {
        public void addProperty(final Object o, final Object b) { }
    }

    public static void clGetDeviceIDs(final Object a, final Object b, final Object c, final Object d, final Object e) { }

    public static cl_context clCreateContext(final Object a, final Object b, final Object c, final Object d, final Object e, final Object f) { return new cl_context(); }

    public static cl_command_queue clCreateCommandQueue(final Object a, final Object b, final Object c, final Object d) { return new cl_command_queue(); }

    public static cl_program clCreateProgramWithSource(final Object a, final Object b, final Object c, final Object d, final Object e) { return new cl_program(); }

    public static void clBuildProgram(final Object a, final Object b, final Object c, final Object d, final Object e, final Object f) { }

    public static cl_kernel clCreateKernel(final Object a, final Object b, final Object c) { return new cl_kernel(); }

    public static void clReleaseProgram(final Object o) { }

    public static cl_mem clCreateBuffer(final Object a, final Object b, final Object c, final Object d, final Object e) { return new cl_mem(); }

    public static void clSetKernelArg(final Object a, final Object b, final Object c, final Object d) { }

    public static void clReleaseKernel(final Object a) { }

    public static void clReleaseCommandQueue(final Object a) { }

    public static void clFinish(final Object a) { }

    public static void clEnqueueWriteBuffer(final Object a, final Object b, final Object c, final Object d, final Object e, final Object f, final Object g, final Object h, final Object i) { }

    public static void clEnqueueNDRangeKernel(final Object a, final Object b, final Object c, final Object d, final Object e, final Object f, final Object g, final Object h, final Object i) { }

    public static void clEnqueueReadBuffer(final Object a, final Object b, final Object c, final Object d, final Object e, final Object f, final Object g, final Object h, final Object i) { }

}
*/
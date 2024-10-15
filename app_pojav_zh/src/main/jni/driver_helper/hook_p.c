//NOLINTBEGIN
static void* (*android_dlopen_ext_p)(const char* filename,
                                  int flags,
                                  const android_dlextinfo* extinfo,
                                  const void* caller_addr);
static struct android_namespace_t* (*android_get_exported_namespace_p)(const char* name);
//NOLINTEND

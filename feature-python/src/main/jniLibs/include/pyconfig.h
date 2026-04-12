/* pyconfig.h for Android - Minimal configuration */

#ifndef Py_CONFIG_H
#define Py_CONFIG_H

/* Android platform configuration */
#define PLATFORM "android"

/* Size of various types */
#define SIZEOF_LONG 8
#define SIZEOF_VOID_P 8
#define SIZEOF_TIME_T 8
#define SIZEOF_SIZE_T 8
#define SIZEOF_OFF_T 8
#define SIZEOF_WCHAR_T 4  /* Android uses UTF-32 for wchar_t */
#define SIZEOF_CHAR 1
#define SIZEOF_SHORT 2
#define SIZEOF_INT 4
#define SIZEOF_LONG_LONG 8
#define SIZEOF_FLOAT 4
#define SIZEOF_DOUBLE 8

/* Define if you have pthreads */
#define HAVE_PTHREAD 1
#define HAVE_PTHREAD_ATFORK 1
#define HAVE_PTHREAD_CONDATTR_SETCLOCK 1
#define HAVE_PTHREAD_DESTRUCTOR 0
#define HAVE_PTHREAD_GETCPUCLOCKID 1
#define HAVE_PTHREAD_H 1
#define HAVE_PTHREAD_INIT 1
#define HAVE_PTHREAD_KILL 1
#define HAVE_PTHREAD_SIGMASK 1

/* Native threads support */
#define PY_HAVE_THREAD_NATIVE_ID 1
#define HAVE_THREAD_LOCAL 1
#define NATIVE_TSS_KEY_T pthread_key_t

/* Define if you have gettimeofday */
#define HAVE_GETTIMEOFDAY 1

/* Define if you have sigaction */
#define HAVE_SIGACTION 1

/* Define if you have clock_gettime */
#define HAVE_CLOCK_GETTIME 1

/* Define if you have nanosleep */
#define HAVE_NANOSLEEP 1

/* Define if you have setitimer */
#define HAVE_SETITIMER 1

/* Define if you have alarm */
#define HAVE_ALARM 1

/* Math functions */
#define HAVE_ACOSH 1
#define HAVE_ASINH 1
#define HAVE_ATANH 1
#define HAVE_COPYSIGN 1
#define HAVE_ERF 1
#define HAVE_GAMMA 1
#define HAVE_HYPOT 1
#define HAVE_LGAMMA 1
#define HAVE_ROUND 1
#define HAVE_TGAMMA 1
#define HAVE_TRUNC 1

/* Other functions */
#define HAVE_ACCEPT4 1
#define HAVE_PIPE2 1
#define HAVE_READLINK 1
#define HAVE_REALPATH 1
#define HAVE_SYMLINK 1
#define HAVE_UTIMENSAT 1
#define HAVE_FSTATAT 1
#define HAVE_LINKAT 1
#define HAVE_MKDIRAT 1
#define HAVE_OPENAT 1
#define HAVE_RENAMEAT 1
#define HAVE_UNLINKAT 1
#define HAVE_FACCESSAT 1
#define HAVE_CHOWN 1
#define HAVE_LCHOWN 1
#define HAVE_FCHOWN 1
#define HAVE_FCHOWNAT 1
#define HAVE_FDOPENDIR 1
#define HAVE_DIRFD 1
#define HAVE_FTELLO 1
#define HAVE_FSEEKO 1
#define HAVE_STATVFS 1
#define HAVE_POSIX_FADVISE 1
#define HAVE_POSIX_FALLOCATE 1
#define HAVE_SCHED_GET_PRIORITY_MAX 1
#define HAVE_SCHED_GET_PRIORITY_MIN 1
#define HAVE_STRFTIME 1
#define HAVE_WCSCOLL 1
#define HAVE_WCSXFRM 1
#define HAVE_DUP2 1
#define HAVE_GETCWD 1
#define HAVE_READV 1
#define HAVE_WRITEV 1
#define HAVE_SETEGID 1
#define HAVE_SETEUID 1
#define HAVE_SETREGID 1
#define HAVE_SETREUID 1
#define HAVE_WAITPID 1
#define HAVE_GETGROUPS 1
#define HAVE_SETGROUPS 1
#define HAVE_INITGROUPS 1
#define HAVE_KILL 1
#define HAVE_KILLPG 1
#define HAVE_GETPGRP 1
#define HAVE_SETPGRP 1
#define HAVE_TIMES 1
#define HAVE_FCNTL 1
#define HAVE_EPOLL_CREATE1 1
#define HAVE_EPOLL_CTL 1
#define HAVE_EPOLL_PWAIT 1
#define HAVE_POLL 1
#define HAVE_SELECT 1
#define HAVE_SOCKET 1
#define HAVE_GETADDRINFO 1
#define HAVE_GETNAMEINFO 1
#define HAVE_INET_PTON 1
#define HAVE_INET_NTOP 1
#define HAVE_IPV6 1
#define HAVE_SOCKADDR_STORAGE 1
#define HAVE_IF_NAMEINDEX 1
#define HAVE_GETPEERNAME 1
#define HAVE_GETSOCKNAME 1
#define HAVE_GETSOCKOPT 1
#define HAVE_SETSOCKOPT 1
#define HAVE_SHUTDOWN 1
#define HAVE_ACCEPT 1
#define HAVE_BIND 1
#define HAVE_CONNECT 1
#define HAVE_LISTEN 1
#define HAVE_RECV 1
#define HAVE_RECVFROM 1
#define HAVE_SEND 1
#define HAVE_SENDTO 1
#define HAVE_SENDFILE 1

/* SSL support */
#define HAVE_OPENSSL_SSL_H 1
#define HAVE_OPENSSL_RAND_H 1
#define HAVE_OPENSSL_ERR_H 1
#define HAVE_OPENSSL_CRYPTO_H 1

/* Zlib support */
#define HAVE_ZLIB_H 1
#define HAVE_LIBZ 1

/* BZip2 support */
/* #undef HAVE_BZLIB_H */
/* #undef HAVE_LIBBZ2 */

/* LZMA support */
/* #undef HAVE_LZMA_H */
/* #undef HAVE_LIBLZMA */

/* Termios support */
#define HAVE_TERMIOS_H 1
#define HAVE_TCGETPGRP 1
#define HAVE_TCSETPGRP 1

/* Syslog support */
#define HAVE_SYSLOG_H 1

/* Memory management */
#define HAVE_MMAP 1
#define HAVE_MREMAP 1

/* Dynamic loading */
#define HAVE_DLFCN_H 1
#define HAVE_DLOPEN 1
#define HAVE_DLSYM 1
#define HAVE_DLERROR 1
#define HAVE_DLCLOSE 1

/* Wide character support */
#define HAVE_WCHAR_H 1
#define HAVE_MBRTOWC 1
#define HAVE_WCRTOMB 1
#define HAVE_WCSTOUL 1
#define HAVE_FWIDE 1
#define HAVE_WMEMCMP 1

/* Locale support */
#define HAVE_LOCALE_H 1
#define HAVE_LANGINFO_H 1
#define HAVE_NL_LANGINFO 1
#define HAVE_CODESET 1

/* Endianness */
#define WORDS_BIGENDIAN 0

/* Alignment */
#define ALIGNOF_LONG 8
#define ALIGNOF_SIZE_T 8
#define ALIGNOF_VOID_P 8
#define ALIGNOF_DOUBLE 8

/* Format modifiers */
#define PY_FORMAT_SIZE_T "z"

/* Python version - removed to avoid conflict with patchlevel.h */
/* #define PY_MAJOR_VERSION 3 */
/* #define PY_MINOR_VERSION 14 */
/* #define PY_MICRO_VERSION 0 */
/* #define PY_RELEASE_LEVEL 0xF */
/* #define PY_RELEASE_SERIAL 0 */

/* Build flags */
#define Py_DEBUG 0
#define Py_REF_DEBUG 0  /* Disable reference count debugging */
#define COUNT_ALLOCS 0
#define Py_TRACE_REFS 0

#endif /* !Py_CONFIG_H */

#include <jni.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static jlong make_handle(pid_t pid, int master) {
    return ((jlong)(unsigned long long)pid << 32) | ((jlong)master & 0xffffffffLL);
}

JNIEXPORT jlong JNICALL
Java_com_dshpet_android_PtySession_nativeStart(JNIEnv *env, jclass clazz) {
    int master = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (master < 0) return -1;

    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        close(master);
        return -1;
    }

    const char *slave_name = ptsname(master);
    if (!slave_name) {
        close(master);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(master);
        return -1;
    }

    if (pid == 0) {
        close(master);
        setsid();
        int slave = open(slave_name, O_RDWR);
        if (slave < 0) _exit(127);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > 2) close(slave);
        setenv("TERM", "xterm", 1);
        setenv("PATH", "/system/bin:/system/xbin:/sbin:/vendor/bin:/data/local/tmp", 1);
        char *argv[] = {"su", NULL};
        execvp("su", argv);
        _exit(127);
    }

    return make_handle(pid, master);
}

JNIEXPORT void JNICALL
Java_com_dshpet_android_PtySession_nativeDestroy(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle <= 0) return;
    pid_t pid = (pid_t)((unsigned long long)handle >> 32);
    if (pid > 0) {
        kill(pid, SIGHUP);
        kill(pid, SIGTERM);
        waitpid(pid, NULL, WNOHANG);
    }
}

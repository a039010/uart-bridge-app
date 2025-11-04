
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <sys/select.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

static int uart_fd = -1;

static int configure_port(int fd, int baud) {
    struct termios tty;
    if (tcgetattr(fd, &tty) != 0) return -1;
    speed_t speed;
    switch (baud) {
        case 115200: speed = B115200; break;
        case 57600: speed = B57600; break;
        case 38400: speed = B38400; break;
        case 19200: speed = B19200; break;
        case 9600: speed = B9600; break;
        default: speed = B115200; break;
    }
    cfsetospeed(&tty, speed);
    cfsetispeed(&tty, speed);
    tty.c_cflag = (tty.c_cflag & ~CSIZE) | CS8;
    tty.c_cflag &= ~PARENB;
    tty.c_cflag &= ~CSTOPB;
    tty.c_cflag &= ~CRTSCTS;
    tty.c_cflag |= CLOCAL | CREAD;
    tty.c_lflag &= ~(ICANON | ECHO | ECHOE | ISIG);
    tty.c_iflag &= ~(IXON | IXOFF | IXANY);
    tty.c_oflag &= ~OPOST;
    tty.c_cc[VMIN] = 0;
    tty.c_cc[VTIME] = 1;
    if (tcsetattr(fd, TCSANOW, &tty) != 0) return -1;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_example_uartbridge_IUARTBridge_uartOpen(JNIEnv *env, jobject thiz, jstring port, jint baud) {
    const char *p = (*env)->GetStringUTFChars(env, port, 0);
    int fd = open(p, O_RDWR | O_NOCTTY | O_NONBLOCK);
    (*env)->ReleaseStringUTFChars(env, port, p);
    if (fd < 0) return -1;
    if (configure_port(fd, baud) != 0) { close(fd); return -2; }
    int flags = fcntl(fd, F_GETFL, 0);
    flags &= ~O_NONBLOCK;
    fcntl(fd, F_SETFL, flags);
    uart_fd = fd;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_example_uartbridge_IUARTBridge_uartClose(JNIEnv *env, jobject thiz) {
    if (uart_fd >= 0) { close(uart_fd); uart_fd = -1; return 0; }
    return -1;
}

JNIEXPORT jint JNICALL Java_com_example_uartbridge_IUARTBridge_uartWrite(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (uart_fd < 0) return -1;
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    int written = write(uart_fd, buf, len);
    (*env)->ReleaseByteArrayElements(env, data, buf, 0);
    return written;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_uartbridge_IUARTBridge_uartRead(JNIEnv *env, jobject thiz, jint len) {
    if (uart_fd < 0) return NULL;
    if (len <= 0) return NULL;
    unsigned char *buf = malloc(len);
    int r = read(uart_fd, buf, len);
    if (r <= 0) { free(buf); return NULL; }
    jbyteArray out = (*env)->NewByteArray(env, r);
    (*env)->SetByteArrayRegion(env, out, 0, r, (jbyte*)buf);
    free(buf);
    return out;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_uartbridge_IUARTBridge_uartReadAvailable(JNIEnv *env, jobject thiz, jint maxlen, jint timeout_ms) {
    if (uart_fd < 0) return NULL;
    if (maxlen <= 0) return NULL;
    fd_set set;
    struct timeval tv;
    FD_ZERO(&set);
    FD_SET(uart_fd, &set);
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    int rv = select(uart_fd + 1, &set, NULL, NULL, &tv);
    if (rv <= 0) return NULL;
    int toread = maxlen;
    unsigned char *buf = malloc(toread);
    int r = read(uart_fd, buf, toread);
    if (r <= 0) { free(buf); return NULL; }
    jbyteArray out = (*env)->NewByteArray(env, r);
    (*env)->SetByteArrayRegion(env, out, 0, r, (jbyte*)buf);
    free(buf);
    return out;
}

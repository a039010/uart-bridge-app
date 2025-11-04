@if "%DEBUG%" == "" @echo off
set CLASSPATH=%~dp0\gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

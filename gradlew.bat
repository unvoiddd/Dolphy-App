@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)
"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo Java not found. Set JAVA_HOME to JDK 17 or newer.
  exit /b 1
)
"%JAVA_EXE%" -Xmx64m -Xms64m -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %errorlevel%

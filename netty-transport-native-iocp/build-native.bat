rem "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"

echo off
set ARCH=%1

set PROJECT_DIR=%CD%

PUSHD

IF "%ARCH%" == "x86_64" (
  mkdir build\native-build-windows-x86_64
  cd build\native-build-windows-x86_64
  set CMAKE_ARCH=x64
) ELSE (
  mkdir build\native-build-windows-x86_32
  cd build\native-build-windows-x86_32
  set CMAKE_ARCH=Win32
)


REM Get Java Home for JNI headers
FOR /F "tokens=*" %%g IN ('where java') do (SET JAVA_PATH=%%g)
SET JAVA_HOME=%JAVA_PATH:\bin\java.exe=%
ECHO Using JAVA_HOME: %JAVA_HOME%

SET JNI_INCLUDE_DIR=%JAVA_HOME%\include

cmake ..\..\src\main\c -A %CMAKE_ARCH% -DJNI_INCLUDE_DIR="%JNI_INCLUDE_DIR%"
set EXIT_CODE=%errorlevel%
if %EXIT_CODE% neq 0 (
  POPD
  exit /b %EXIT_CODE%
)

cmake --build . --config RelWithDebInfo
set EXIT_CODE=%errorlevel%
if %EXIT_CODE% neq 0 (
  POPD
  exit /b %EXIT_CODE%
)

mkdir %PROJECT_DIR%\src\main\resources\META-INF\native 2>nul
mkdir %PROJECT_DIR%\src\main\debug 2>nul

COPY /Y RelWithDebInfo\netty_transport_native_iocp_%ARCH%.dll %PROJECT_DIR%\src\main\resources\META-INF\native\
COPY /Y RelWithDebInfo\netty_transport_native_iocp_%ARCH%.pdb %PROJECT_DIR%\src\main\debug\

POPD

exit /b 0

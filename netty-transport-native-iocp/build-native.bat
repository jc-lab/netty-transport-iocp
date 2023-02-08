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

cmake ..\..\src\main\c -A %CMAKE_ARCH%
set RC=%errorlevel%
if %RC% neq 0 (
  POPD
  exit /b %RC%
)

cmake --build . --config RelWithDebInfo
set RC=%errorlevel%
if %RC% neq 0 (
  POPD
  exit /b %RC%
)

COPY /Y RelWithDebInfo\netty_transport_native_iocp_%ARCH%.dll %PROJECT_DIR%\src\main\resources\META-INF\native\
COPY /Y RelWithDebInfo\netty_transport_native_iocp_%ARCH%.pdb %PROJECT_DIR%\src\main\debug\

POPD
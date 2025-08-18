# Sapier Development Workflow

## Handling R.jar Locking Issues

This document outlines the development workflow to avoid the persistent R.jar locking issues encountered during the build process.

## Quick Solutions

### Option 1: Direct Install and Run

Use the provided script to install and launch the app directly:

```bash
# To install and run the last successfully built APK
./direct_install_and_run.bat

# To attempt a build first, then install and run
./direct_install_and_run.bat build
```

### Option 2: Direct Compile and Install

Use the advanced script that attempts to compile with minimal resource processing:

```bash
# To install and run the last successfully built APK
./direct_compile_and_install.bat

# To attempt a build with resource processing skipping, then install and run
./direct_compile_and_install.bat build
```

### Option 3: Skip Resource Processing

Build the app with resource processing disabled:

```bash
./gradlew assembleDebug "-PskipResourceProcessing=true" --info
```

### Option 4: Specify ABI Filters via Command Line

Build with specific ABI filters:

```bash
./gradlew assembleDebug "-PabiFilters=x86_64,arm64-v8a" --info
```

## Detailed Workflow

### 1. Preventing R.jar Locking

The following measures have been implemented to prevent R.jar locking:

- Added a `forceClean` task that deletes R.jar files
- Added garbage collection calls to help release file handles
- Added a delay after file deletion to ensure handles are released
- Implemented hooks before and after resource processing tasks
- Added support for skipping resource processing entirely
- Added command-line control for ABI filters

### 2. Development Process

Follow these steps for a smooth development experience:

1. **Close Android Studio** before building from command line
2. Use the direct install scripts for quick iteration
3. If making UI changes that require resource processing:
   - Restart your computer to ensure no lingering file locks
   - Use `./gradlew clean` before building
   - Try the `-PabiFilters` approach if specific ABIs are needed

### 3. Troubleshooting

If you encounter R.jar locking issues:

1. **Kill Java processes**:
   ```bash
   taskkill /F /IM java.exe
   ```

2. **Reboot your device**:
   ```bash
   adb reboot
   adb wait-for-device
   ```

3. **Direct ADB commands**:
   ```bash
   # Check if app is installed
   adb shell pm list packages | findstr com.example.sapier
   
   # Launch the app directly
   adb shell am start -n com.example.sapier/.MainActivity
   ```

4. **Clean build directory**:
   ```bash
   # Try to force delete the build directory
   Remove-Item -Path "app\build" -Recurse -Force -ErrorAction SilentlyContinue
   ```

### 4. Best Practices

- Make code changes in small batches to minimize build frequency
- Consider using the direct install scripts for testing code changes
- For resource changes, be prepared to restart your development environment
- Keep Android Studio closed when building from command line
- Use the `-PskipResourceProcessing=true` flag when only changing code (not resources)

## Technical Details

### Modified Build Configuration

The `build.gradle.kts` file has been modified to:

1. Add a `forceClean` task that deletes R.jar files and triggers garbage collection
2. Add support for the `skipResourceProcessing` property
3. Add hooks before and after resource processing tasks
4. Add support for command-line ABI filter specification

### Direct Install Scripts

Two scripts have been provided:

1. `direct_install_and_run.bat` - Simple script to install and launch the app
2. `direct_compile_and_install.bat` - Advanced script with more options

These scripts help bypass the problematic resource processing step during development.
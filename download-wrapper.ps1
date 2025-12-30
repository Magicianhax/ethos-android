# Download Gradle Wrapper JAR
$wrapperJarUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
$wrapperJarPath = "gradle\wrapper\gradle-wrapper.jar"

Write-Host "Downloading gradle-wrapper.jar..."
try {
    New-Item -ItemType Directory -Force -Path "gradle\wrapper" | Out-Null
    Invoke-WebRequest -Uri $wrapperJarUrl -OutFile $wrapperJarPath
    Write-Host "✓ Downloaded gradle-wrapper.jar successfully!"
    Write-Host "You can now run: .\gradlew.bat assembleRelease"
} catch {
    Write-Host "✗ Error downloading: $_"
    Write-Host ""
    Write-Host "Alternative: Use Android Studio to open the project,"
    Write-Host "or install Gradle and run: gradle wrapper"
}



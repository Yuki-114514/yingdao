require "fileutils"
require "xcodeproj"

project_root = File.expand_path("..", __dir__)
project_path = File.join(project_root, "YingDaoIOS.xcodeproj")
FileUtils.rm_rf(project_path)

project = Xcodeproj::Project.new(project_path)
project.root_object.attributes["LastUpgradeCheck"] = "1600"
project.root_object.attributes["ORGANIZATIONNAME"] = "Yuki"

app_target = project.new_target(:application, "YingDaoIOS", :ios, "17.0")

app_target.build_configurations.each do |config|
  settings = config.build_settings
  settings["PRODUCT_BUNDLE_IDENTIFIER"] = "com.yuki.yingdao.ios"
  settings["INFOPLIST_FILE"] = "YingDaoIOS/Info.plist"
  settings["SWIFT_VERSION"] = "6.0"
  settings["IPHONEOS_DEPLOYMENT_TARGET"] = "17.0"
  settings["CODE_SIGN_STYLE"] = "Automatic"
  settings["DEVELOPMENT_TEAM"] = ""
  settings["ASSETCATALOG_COMPILER_APPICON_NAME"] = "AppIcon"
  settings["GENERATE_INFOPLIST_FILE"] = "NO"
  settings["TARGETED_DEVICE_FAMILY"] = "1"
  settings["ENABLE_PREVIEWS"] = "YES"
end

main_group = project.main_group
app_group = main_group.new_group("YingDaoIOS", "YingDaoIOS")
core_group = main_group.new_group("Core", "Core")
app_app_group = app_group.new_group("App", "App")
camera_group = app_group.new_group("Camera", "Camera")
results_group = app_group.new_group("Results", "Results")
library_group = app_group.new_group("Library", "Library")

app_files = {
  app_group => ["YingDaoIOSApp.swift"],
  app_app_group => ["YingDaoRoute.swift", "YingDaoAppModel.swift", "RootView.swift"],
  camera_group => ["CameraSessionController.swift", "CameraPreviewView.swift", "LiveGuideOverlay.swift", "CameraHomeView.swift"],
  results_group => ["CaptureResultView.swift"],
  library_group => ["PhotoThumbnailCard.swift", "PhotoLibraryView.swift"],
}

core_files = [
  "GuidePreset.swift",
  "CaptureReview.swift",
  "CapturedPhoto.swift",
  "CameraWorkflowModel.swift",
]

app_files.each do |group, files|
  files.each do |filename|
    file_ref = group.new_file(filename)
    app_target.source_build_phase.add_file_reference(file_ref)
  end
end

core_files.each do |relative_path|
  file_ref = core_group.new_file(relative_path)
  app_target.source_build_phase.add_file_reference(file_ref)
end

assets_ref = app_group.new_file("Assets.xcassets")
app_target.resources_build_phase.add_file_reference(assets_ref)

project.save
puts "Generated #{project_path}"

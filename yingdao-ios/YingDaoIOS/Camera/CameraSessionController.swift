@preconcurrency import AVFoundation
import Foundation
import Observation

@MainActor
@Observable
final class CameraSessionController {
    enum CameraStatus: Equatable {
        case idle
        case requestingPermission
        case configuring
        case ready
        case denied
        case failed(String)
    }

    var status: CameraStatus = .idle
    var isCapturing = false
    var lastErrorMessage: String?

    @ObservationIgnored private let sessionQueue = DispatchQueue(label: "com.yuki.yingdao.ios.camera.session")
    @ObservationIgnored nonisolated(unsafe) private let session = AVCaptureSession()
    @ObservationIgnored nonisolated(unsafe) private let photoOutput = AVCapturePhotoOutput()
    @ObservationIgnored private let configurationState = SessionConfigurationState()
    private var captureDelegates: [Int64: PhotoCaptureProcessor] = [:]

    var canCapture: Bool {
        status == .ready && !isCapturing
    }

    var previewSession: AVCaptureSession {
        session
    }

    deinit {
        let session = session
        let queue = sessionQueue
        queue.async {
            if session.isRunning {
                session.stopRunning()
            }
        }
    }

    func prepareSession() async {
        guard status != .ready && status != .configuring else { return }
        lastErrorMessage = nil

        let authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
        switch authorizationStatus {
        case .authorized:
            await configureAndStartSession()
        case .notDetermined:
            status = .requestingPermission
            let granted = await requestVideoPermission()
            status = granted ? .idle : .denied
            if granted {
                await configureAndStartSession()
            }
        case .denied, .restricted:
            status = .denied
        @unknown default:
            status = .failed("系统返回了无法识别的相机权限状态。")
        }
    }

    func capturePhoto() async throws -> Data {
        guard canCapture else {
            throw CameraControllerError.unavailable
        }

        isCapturing = true
        defer { isCapturing = false }

        let settings = AVCapturePhotoSettings()
        settings.photoQualityPrioritization = .quality

        return try await withCheckedThrowingContinuation { continuation in
            let delegate = PhotoCaptureProcessor { [weak self] result in
                Task { @MainActor in
                    guard let self else { return }
                    self.captureDelegates.removeValue(forKey: settings.uniqueID)

                    switch result {
                    case .success(let imageData):
                        continuation.resume(returning: imageData)
                    case .failure(let error):
                        let message = error.localizedDescription
                        self.lastErrorMessage = message
                        continuation.resume(throwing: error)
                    }
                }
            }

            captureDelegates[settings.uniqueID] = delegate
            photoOutput.capturePhoto(with: settings, delegate: delegate)
        }
    }

    func stopSession() {
        sessionQueue.async { [session] in
            if session.isRunning {
                session.stopRunning()
            }
        }
    }

    private func configureAndStartSession() async {
        status = .configuring

        do {
            try await configureSessionIfNeeded()
            try await startSessionIfNeeded()
            status = .ready
        } catch {
            let message = error.localizedDescription
            lastErrorMessage = message
            status = .failed(message)
        }
    }

    private func requestVideoPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVCaptureDevice.requestAccess(for: .video) { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    private func configureSessionIfNeeded() async throws {
        if configurationState.isConfigured { return }

        let session = self.session
        let photoOutput = self.photoOutput
        let configurationState = self.configurationState

        try await withCheckedThrowingContinuation { continuation in
            sessionQueue.async {
                do {
                    session.beginConfiguration()
                    defer { session.commitConfiguration() }

                    session.sessionPreset = .photo

                    guard let device = AVCaptureDevice.default(
                        .builtInWideAngleCamera,
                        for: .video,
                        position: .back
                    ) else {
                        throw CameraControllerError.noBackCamera
                    }

                    let input = try AVCaptureDeviceInput(device: device)
                    if session.canAddInput(input) {
                        session.addInput(input)
                    } else {
                        throw CameraControllerError.cannotAddInput
                    }

                    photoOutput.maxPhotoQualityPrioritization = .quality
                    if session.canAddOutput(photoOutput) {
                        session.addOutput(photoOutput)
                    } else {
                        throw CameraControllerError.cannotAddOutput
                    }

                    configurationState.isConfigured = true
                    continuation.resume()
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    private func startSessionIfNeeded() async throws {
        let session = self.session

        try await withCheckedThrowingContinuation { continuation in
            sessionQueue.async {
                if !session.isRunning {
                    session.startRunning()
                }
                continuation.resume()
            }
        }
    }
}

private final class SessionConfigurationState: @unchecked Sendable {
    var isConfigured = false
}

private enum CameraControllerError: LocalizedError {
    case unavailable
    case noBackCamera
    case cannotAddInput
    case cannotAddOutput
    case failedToProcessPhoto

    var errorDescription: String? {
        switch self {
        case .unavailable:
            return "相机还没有准备好，请稍后再试。"
        case .noBackCamera:
            return "当前设备找不到可用的后置相机。"
        case .cannotAddInput:
            return "相机输入初始化失败。"
        case .cannotAddOutput:
            return "相机输出初始化失败。"
        case .failedToProcessPhoto:
            return "照片处理失败，请重试。"
        }
    }
}

private final class PhotoCaptureProcessor: NSObject, AVCapturePhotoCaptureDelegate {
    private let completion: (Result<Data, Error>) -> Void

    init(completion: @escaping (Result<Data, Error>) -> Void) {
        self.completion = completion
    }

    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?,
    ) {
        if let error {
            completion(.failure(error))
            return
        }

        guard let imageData = photo.fileDataRepresentation() else {
            completion(.failure(CameraControllerError.failedToProcessPhoto))
            return
        }

        completion(.success(imageData))
    }
}

import AppKit
import Foundation

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)

guard CommandLine.arguments.count > 1 else {
    fatalError("Usage: swift scripts/generate_logo_assets.swift /absolute/path/to/logo-lockup.png")
}

let sourceURL = URL(fileURLWithPath: CommandLine.arguments[1])
guard let source = NSImage(contentsOf: sourceURL), let cgSource = source.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    fatalError("Unable to read logo source at \(sourceURL.path)")
}

func ensureDirectory(_ path: String) {
    let url = root.appendingPathComponent(path)
    try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
}

func bitmap(width: Int, height: Int, draw: (CGContext) -> Void) -> NSImage {
    guard let context = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else {
        fatalError("Unable to create bitmap")
    }
    context.interpolationQuality = .high
    draw(context)

    guard let cgImage = context.makeImage() else {
        fatalError("Unable to create image")
    }
    return NSImage(cgImage: cgImage, size: NSSize(width: width, height: height))
}

func savePNG(_ image: NSImage, _ path: String) {
    let url = root.appendingPathComponent(path)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard
        let tiff = image.tiffRepresentation,
        let rep = NSBitmapImageRep(data: tiff),
        let data = rep.representation(using: .png, properties: [:])
    else {
        fatalError("Unable to encode \(path)")
    }
    try! data.write(to: url)
}

func resized(_ image: NSImage, size: Int) -> NSImage {
    guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        fatalError("Unable to read source image")
    }
    return bitmap(width: size, height: size) { context in
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: size, height: size))
    }
}

func cropIcon() -> NSImage {
    let crop = CGRect(x: 112, y: 614 - 476, width: 360, height: 360)
    guard let icon = cgSource.cropping(to: crop) else {
        fatalError("Unable to crop icon")
    }
    return NSImage(cgImage: icon, size: NSSize(width: 360, height: 360))
}

let icon = cropIcon()
savePNG(source, "docs/assets/sundial-logo-lockup.png")
savePNG(resized(icon, size: 1024), "docs/assets/sundial-icon.png")

let androidSizes = [
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
]

for (bucket, size) in androidSizes {
    let image = resized(icon, size: size)
    savePNG(image, "androidApp/src/androidMain/res/mipmap-\(bucket)/ic_launcher.png")
    savePNG(image, "androidApp/src/androidMain/res/mipmap-\(bucket)/ic_launcher_round.png")
    savePNG(image, "androidApp/src/androidMain/res/mipmap-\(bucket)/ic_launcher_foreground.png")
}

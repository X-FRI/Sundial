import AppKit
import Foundation

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)

guard CommandLine.arguments.count > 1 else {
    fatalError("Usage: swift scripts/generate_logo_assets.swift /absolute/path/to/logo-lockup-or-icon.png")
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

func padded(
    _ image: NSImage,
    size: Int,
    scale: CGFloat = 0.84,
    visualOffsetX: CGFloat = 0,
    visualOffsetY: CGFloat = 0,
) -> NSImage {
    guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        fatalError("Unable to read source image")
    }
    let drawSize = CGFloat(size) * scale
    let origin = (CGFloat(size) - drawSize) / 2
    return bitmap(width: size, height: size) { context in
        context.setFillColor(CGColor(red: 1.0, green: 0.972, blue: 0.93, alpha: 1.0))
        context.fill(CGRect(x: 0, y: 0, width: size, height: size))
        context.draw(
            cgImage,
            in: CGRect(
                x: origin + CGFloat(size) * visualOffsetX,
                y: origin + CGFloat(size) * visualOffsetY,
                width: drawSize,
                height: drawSize,
            ),
        )
    }
}

func cropIcon() -> NSImage {
    let crop = CGRect(x: 112, y: 614 - 476, width: 360, height: 360)
    guard let icon = cgSource.cropping(to: crop) else {
        fatalError("Unable to crop icon")
    }
    return NSImage(cgImage: icon, size: NSSize(width: 360, height: 360))
}

let isIconSource = abs(cgSource.width - cgSource.height) <= 4
let icon = isIconSource ? source : cropIcon()
let projectIcon = isIconSource ? resized(icon, size: 1024) : padded(icon, size: 1024)

if !isIconSource {
    savePNG(source, "docs/assets/sundial-logo-lockup.png")
}
savePNG(projectIcon, "docs/assets/sundial-icon.png")
savePNG(projectIcon, "desktopApp/src/jvmMain/resources/brand/sundial-icon.png")

let androidSizes = [
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
]

for (bucket, size) in androidSizes {
    let image = isIconSource ? resized(icon, size: size) : padded(icon, size: size)
    savePNG(image, "androidApp/src/androidMain/res/mipmap-\(bucket)/ic_launcher.png")
    savePNG(image, "androidApp/src/androidMain/res/mipmap-\(bucket)/ic_launcher_round.png")
    savePNG(image, "androidApp/src/androidMain/res/mipmap-\(bucket)/ic_launcher_foreground.png")
}

let iconsetURL = root.appendingPathComponent("desktopApp/build/generated/sundial.iconset")
try? FileManager.default.removeItem(at: iconsetURL)
try! FileManager.default.createDirectory(at: iconsetURL, withIntermediateDirectories: true)

let iconsetSizes = [
    ("icon_16x16.png", 16),
    ("icon_16x16@2x.png", 32),
    ("icon_32x32.png", 32),
    ("icon_32x32@2x.png", 64),
    ("icon_128x128.png", 128),
    ("icon_128x128@2x.png", 256),
    ("icon_256x256.png", 256),
    ("icon_256x256@2x.png", 512),
    ("icon_512x512.png", 512),
    ("icon_512x512@2x.png", 1024),
]

for (name, size) in iconsetSizes {
    let image = isIconSource ? resized(icon, size: size) : padded(icon, size: size)
    savePNG(image, "desktopApp/build/generated/sundial.iconset/\(name)")
}

let iconutil = Process()
iconutil.executableURL = URL(fileURLWithPath: "/usr/bin/iconutil")
iconutil.arguments = [
    "-c",
    "icns",
    iconsetURL.path,
    "-o",
    root.appendingPathComponent("desktopApp/src/jvmMain/resources/icon.icns").path,
]
try! iconutil.run()
iconutil.waitUntilExit()
if iconutil.terminationStatus != 0 {
    fatalError("iconutil failed with status \(iconutil.terminationStatus)")
}

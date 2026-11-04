import Shared
import SwiftTerm
import UIKit

@MainActor
final class SwiftTermViews: @preconcurrency TerminalViews {
    func create(foreground: Int32, background: Int32, scrollback: Int32, keys: @escaping (Data) -> Void, resized: @escaping (KotlinInt, KotlinInt) -> Void) -> any TerminalScreen {
        SwiftTermScreen(foreground: foreground, background: background, scrollback: Int(scrollback), keys: keys, resized: resized)
    }
}

@MainActor
private final class SwiftTermScreen: NSObject, @preconcurrency TerminalScreen, TerminalViewDelegate {
    private let terminal: TerminalView
    private let keys: (Data) -> Void
    private let resized: (KotlinInt, KotlinInt) -> Void

    init(foreground: Int32, background: Int32, scrollback: Int, keys: @escaping (Data) -> Void, resized: @escaping (KotlinInt, KotlinInt) -> Void) {
        terminal = TerminalView(frame: .zero, options: TerminalOptions(scrollback: scrollback))
        self.keys = keys
        self.resized = resized
        super.init()
        terminal.nativeForegroundColor = UIColor(argb: foreground)
        terminal.nativeBackgroundColor = UIColor(argb: background)
        terminal.terminalDelegate = self
    }

    var view: UIView { terminal }

    func show(bytes: Data) {
        terminal.feed(byteArray: ArraySlice(bytes))
    }

    func send(source: TerminalView, data: ArraySlice<UInt8>) {
        keys(Data(data))
    }

    func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {
        resized(KotlinInt(int: Int32(newCols)), KotlinInt(int: Int32(newRows)))
    }

    func clipboardCopy(source: TerminalView, content: Data) {
        UIPasteboard.general.string = String(decoding: content, as: UTF8.self)
    }

    // a program on the router reading the clipboard of the phone is not allowed
    func clipboardRead(source: TerminalView) -> Data? { nil }

    func setTerminalTitle(source: TerminalView, title: String) {}

    func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}

    func scrolled(source: TerminalView, position: Double) {}

    func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {}

    func bell(source: TerminalView) {}

    func iTermContent(source: TerminalView, content: ArraySlice<UInt8>) {}

    func rangeChanged(source: TerminalView, startY: Int, endY: Int) {}
}

private extension UIColor {
    convenience init(argb: Int32) {
        let value = UInt32(bitPattern: argb)
        self.init(red: CGFloat((value >> 16) & 0xff) / 255, green: CGFloat((value >> 8) & 0xff) / 255, blue: CGFloat(value & 0xff) / 255, alpha: CGFloat(value >> 24) / 255)
    }
}

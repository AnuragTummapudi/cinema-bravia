import AppKit
import WebKit

// Thin native window; TV control and credentials stay in the localhost backend.
@MainActor final class RemoteApp: NSObject, NSApplicationDelegate, WKNavigationDelegate {
    var window: NSWindow!
    var webView: WKWebView!
    var backend: Process?
    let remoteURL = URL(string: "http://127.0.0.1:8765")!

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.appearance = NSAppearance(named: .darkAqua)
        buildMenu()
        if let iconURL = Bundle.main.url(forResource: "AppIcon", withExtension: "icns"),
           let image = NSImage(contentsOf: iconURL) { NSApp.applicationIconImage = image }

        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1160, height: 780),
                          styleMask: [.titled, .closable, .miniaturizable, .resizable],
                          backing: .buffered, defer: false)
        window.title = "BRAVIA Remote"
        window.minSize = NSSize(width: 390, height: 600)
        window.backgroundColor = NSColor(calibratedWhite: 0.094, alpha: 1)
        window.collectionBehavior.insert(.fullScreenPrimary)
        window.setFrameAutosaveName("BRAVIA.Remote.Window")
        window.center()
        webView = WKWebView(frame: window.contentView!.bounds)
        webView.autoresizingMask = [.width, .height]
        webView.navigationDelegate = self
        window.contentView = webView
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        Task { if await ensureBackend() { reload() } else { showUnavailable() } }
    }

    func ensureBackend() async -> Bool {
        let health = remoteURL.appendingPathComponent("api/state")
        var request = URLRequest(url: health)
        request.timeoutInterval = 1
        if let (_, response) = try? await URLSession.shared.data(for: request),
           (response as? HTTPURLResponse)?.statusCode == 200 { return true }
        let root = Bundle.main.bundleURL.deletingLastPathComponent()
        let process = Process()
        process.executableURL = root.appendingPathComponent(".venv/bin/python")
        process.arguments = [root.appendingPathComponent("server.py").path]
        process.currentDirectoryURL = root
        let state = root.appendingPathComponent(".state")
        try? FileManager.default.createDirectory(at: state, withIntermediateDirectories: true,
                 attributes: [.posixPermissions: 0o700])
        let logURL = state.appendingPathComponent("server.log")
        if !FileManager.default.fileExists(atPath: logURL.path) {
            FileManager.default.createFile(atPath: logURL.path, contents: nil,
                                          attributes: [.posixPermissions: 0o600])
        }
        if let output = try? FileHandle(forWritingTo: logURL) {
            output.seekToEndOfFile()
            process.standardOutput = output
            process.standardError = output
        }
        do { try process.run(); backend = process }
        catch { return false }

        for _ in 0..<30 {
            if let (_, response) = try? await URLSession.shared.data(for: request),
               (response as? HTTPURLResponse)?.statusCode == 200 { return true }
            try? await Task.sleep(nanoseconds: 200_000_000)
        }
        return false
    }

    func buildMenu() {
        let menu = NSMenu()
        let appItem = NSMenuItem()
        let appMenu = NSMenu()
        appMenu.addItem(withTitle: "Quit BRAVIA Remote", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        appItem.submenu = appMenu
        menu.addItem(appItem)
        let editItem = NSMenuItem()
        let editMenu = NSMenu(title: "Edit")
        for (title, action, key) in [("Cut", "cut:", "x"), ("Copy", "copy:", "c"), ("Paste", "paste:", "v"), ("Select All", "selectAll:", "a")] {
            editMenu.addItem(withTitle: title, action: Selector(action), keyEquivalent: key)
        }
        editItem.submenu = editMenu
        menu.addItem(editItem)
        let viewItem = NSMenuItem()
        let viewMenu = NSMenu(title: "View")
        viewMenu.addItem(withTitle: "Reload", action: #selector(reload), keyEquivalent: "r")
        viewMenu.addItem(withTitle: "Enter Full Screen", action: #selector(toggleFullScreen), keyEquivalent: "f").keyEquivalentModifierMask = [.control, .command]
        viewItem.submenu = viewMenu
        menu.addItem(viewItem)
        NSApp.mainMenu = menu
    }

    @objc func reload() { webView.load(URLRequest(url: remoteURL, cachePolicy: .reloadIgnoringLocalCacheData)) }
    @objc func toggleFullScreen() { window.toggleFullScreen(nil) }
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) { reload() }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { showUnavailable() }
    }
    func showUnavailable() {
        webView.loadHTMLString("""
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>BRAVIA Remote</title></head>
        <body style="margin:0;background:#181818;color:#f5f5f3;font:16px system-ui;display:grid;place-items:center;min-height:100vh"><main style="padding:32px;max-width:480px"><h1 style="font-size:24px;font-weight:600">Remote unavailable</h1><p style="color:#b4b4ae;line-height:1.5">The local server could not be reached. Reopen BRAVIA Remote or try again.</p><a style="color:#f5f5f3" href="http://127.0.0.1:8765/">Reload controls</a></main></body></html>
        """, baseURL: remoteURL)
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
    func applicationWillTerminate(_ notification: Notification) { if backend?.isRunning == true { backend?.terminate() } }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let url = navigationAction.request.url
        if url?.absoluteString == "about:blank" { decisionHandler(.allow); return }
        decisionHandler(url?.host == "127.0.0.1" && url?.port == 8765 && url?.scheme == "http" ? .allow : .cancel)
    }
}

MainActor.assumeIsolated {
    let app = NSApplication.shared
    let delegate = RemoteApp()
    app.delegate = delegate
    withExtendedLifetime(delegate) { app.run() }
}

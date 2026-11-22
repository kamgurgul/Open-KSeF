import UIKit
import ComposeApp

/// Hosts the Compose content and renders the navigation elements (navigation bar title, back and
/// close buttons) above it using system components, so they get the Liquid Glass appearance and
/// are placed like in native apps.
///
/// The navigation bar belongs to a system `UINavigationController` holding an empty placeholder.
/// UIKit places only the bars of its containers, e.g. down the side on iPhone Duo, never a
/// standalone `UINavigationBar`. The whole UI stays in one Compose view below it. Touches that
/// don't hit the items of the bar or the buttons pass through to the Compose view.
@available(iOS 26.0, *)
final class LiquidGlassNavigationViewController: UIViewController, IosNativeNavigationRenderer {

    private static let buttonSize: CGFloat = 44

    private let placeholder = UIViewController()
    private lazy var barsController = UINavigationController(rootViewController: placeholder)
    private var content: UIViewController?
    private var buttons: [String: UIButton] = [:]
    private var buttonActions: [String: () -> Void] = [:]
    private var navigationBarButtons: [String: NavigationBarButton] = [:]
    private var navigationBarTitles: [String: NavigationBarTitle] = [:]
    private var navigationBarSequence = 0
    private var isNavigationBarApplied = false
    private var isNavigationBarUpdateScheduled = false
    private var onBarsInsetsChanged: ((IosNativeBarsInsets) -> Void)?
    private var barsInsets = BarsInsets()
    private var reportedBarsInsets: BarsInsets?

    override var childForStatusBarStyle: UIViewController? { content }

    override var childForHomeIndicatorAutoHidden: UIViewController? { content }

    override func loadView() {
        let view = PassthroughView()
        view.passesThrough = { [weak self] hit in self?.passesThrough(hit) ?? false }
        view.target = { [weak self] in self?.content?.view }
        self.view = view
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        placeholder.view = LayoutReportingView { [weak self] in self?.updateBarsInsets() }
        barsController.navigationBar.tintColor = .label
        barsController.view.backgroundColor = .clear
        addChild(barsController)
        barsController.view.frame = view.bounds
        barsController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(barsController.view)
        barsController.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateBarsInsets()
    }

    /// Adds the Compose content below the native navigation elements.
    func embed(_ content: UIViewController) {
        loadViewIfNeeded()
        self.content = content
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        view.insertSubview(content.view, at: 0)
        NSLayoutConstraint.activate([
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        content.didMove(toParent: self)
        setNeedsStatusBarAppearanceUpdate()
    }

    // MARK: - IosNativeNavigationRenderer

    func setDarkTheme(isDarkTheme: Bool) {
        overrideUserInterfaceStyle = isDarkTheme ? .dark : .light
    }

    func setOnBarsInsetsChangedListener(listener: @escaping (IosNativeBarsInsets) -> Void) {
        onBarsInsetsChanged = listener
        reportedBarsInsets = nil
        reportBarsInsets()
    }

    func setButton(
        id: String,
        systemImageName: String,
        accessibilityLabel: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        alpha: Double,
        onClick: @escaping () -> Void
    ) {
        removeNavigationBarButton(id: id)
        buttonActions[id] = onClick
        let button = buttons[id] ?? createButton(id: id)
        if button.configuration?.image == nil
            || button.accessibilityIdentifier != systemImageName
        {
            button.configuration?.image = UIImage(systemName: systemImageName)
            button.accessibilityIdentifier = systemImageName
        }
        button.accessibilityLabel = accessibilityLabel
        button.alpha = CGFloat(alpha)
        button.isUserInteractionEnabled = alpha > 0
        // Keep the system size of the button and center it in the space reserved by Compose
        let size = Self.buttonSize
        button.frame = CGRect(
            x: x + (width - size) / 2,
            y: y + (height - size) / 2,
            width: size,
            height: size
        )
    }

    func setNavigationBarButton(
        id: String,
        systemImageName: String,
        accessibilityLabel: String,
        isTop: Bool,
        onClick: @escaping () -> Void
    ) {
        buttons.removeValue(forKey: id)?.removeFromSuperview()
        buttonActions[id] = onClick
        let button: NavigationBarButton
        if let existing = navigationBarButtons[id] {
            button = existing
        } else {
            let item = UIBarButtonItem(
                primaryAction: UIAction(image: UIImage(systemName: systemImageName)) {
                    [weak self] _ in self?.buttonActions[id]?()
                }
            )
            button = NavigationBarButton(item: item, systemImageName: systemImageName)
            navigationBarButtons[id] = button
        }
        if button.systemImageName != systemImageName {
            button.systemImageName = systemImageName
            button.item.image = UIImage(systemName: systemImageName)
        }
        button.item.accessibilityLabel = accessibilityLabel
        if button.sequence == 0 || button.isTop != isTop {
            button.isTop = isTop
            navigationBarSequence += 1
            button.sequence = navigationBarSequence
            scheduleNavigationBarUpdate()
        }
    }

    func removeButton(id: String) {
        buttonActions[id] = nil
        buttons.removeValue(forKey: id)?.removeFromSuperview()
        removeNavigationBarButton(id: id)
    }

    func setNavigationBarTitle(id: String, title: String, titleColorArgb: Int32, isTop: Bool) {
        let registration = navigationBarTitles[id] ?? NavigationBarTitle()
        navigationBarTitles[id] = registration
        if registration.title != title || registration.colorArgb != titleColorArgb {
            registration.title = title
            registration.colorArgb = titleColorArgb
            scheduleNavigationBarUpdate()
        }
        if registration.sequence == 0 || registration.isTop != isTop {
            registration.isTop = isTop
            navigationBarSequence += 1
            registration.sequence = navigationBarSequence
            scheduleNavigationBarUpdate()
        }
    }

    func removeNavigationBarTitle(id: String) {
        if navigationBarTitles.removeValue(forKey: id) != nil {
            scheduleNavigationBarUpdate()
        }
    }

    // MARK: - Private

    private func createButton(id: String) -> UIButton {
        var configuration = UIButton.Configuration.glass()
        configuration.cornerStyle = .capsule
        let button = UIButton(
            configuration: configuration,
            primaryAction: UIAction { [weak self] _ in self?.buttonActions[id]?() }
        )
        button.tintColor = .label
        buttons[id] = button
        // Above the bars, so the button gets the touches
        view.addSubview(button)
        return button
    }

    private func removeNavigationBarButton(id: String) {
        if navigationBarButtons.removeValue(forKey: id) != nil {
            scheduleNavigationBarUpdate()
        }
    }

    /// Buttons and titles register while Compose applies a frame, e.g. the ones of the leaving and
    /// of the new top screen, they are applied together so the bar doesn't empty in between.
    private func scheduleNavigationBarUpdate() {
        guard !isNavigationBarUpdateScheduled else { return }
        isNavigationBarUpdateScheduled = true
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            isNavigationBarUpdateScheduled = false
            updateNavigationBar()
        }
    }

    private func updateNavigationBar() {
        let item = navigationBarButtons.values.filter(\.isTop).max { $0.sequence < $1.sequence }?
            .item
        let title = navigationBarTitles.values.filter(\.isTop).max { $0.sequence < $1.sequence }
        let navigationItem = placeholder.navigationItem
        // The bar is never hidden, UIKit would slide it in from the top. It's invisible while
        // empty, so UIKit fades the item in and out where it is instead.
        if navigationItem.leftBarButtonItem !== item {
            navigationItem.setLeftBarButton(item, animated: isNavigationBarApplied)
        }
        if let title {
            barsController.navigationBar.titleTextAttributes = [
                .foregroundColor: UIColor(argb: title.colorArgb)
            ]
        }
        navigationItem.title = title?.title
        isNavigationBarApplied = true
    }

    private func updateBarsInsets() {
        guard isViewLoaded, barsController.isViewLoaded, barsController.view.window != nil
        else { return }
        let navigationBar = barsController.navigationBar
        let frame = navigationBar.convert(navigationBar.bounds, to: view)
        barsInsets = BarsInsets(
            areBarsVertical: frame.height > frame.width,
            navigationBarBottom: navigationBarBottom(frame)
        )
        reportBarsInsets()
    }

    /// Where the content below the navigation bar starts, a Compose top bar shown in the system bar
    /// takes this space. UIKit can lay the bar out next to the status bar, e.g. when it takes only
    /// a corner of the unfolded iPhone Duo. `nil` while the bar runs down a side.
    private func navigationBarBottom(_ frame: CGRect) -> CGFloat? {
        guard frame.width > frame.height else { return nil }
        return frame.maxY
    }

    private func reportBarsInsets() {
        guard let onBarsInsetsChanged, reportedBarsInsets != barsInsets else { return }
        reportedBarsInsets = barsInsets
        onBarsInsetsChanged(
            IosNativeBarsInsets(
                areBarsVertical: barsInsets.areBarsVertical,
                navigationBarBottom: barsInsets.navigationBarBottom.map {
                    KotlinDouble(double: Double($0))
                }
            )
        )
    }

    private func passesThrough(_ hit: UIView) -> Bool {
        guard let barsView = barsController.viewIfLoaded else { return false }
        if hit === barsView { return true }
        // Touches between the items of the navigation bar go to the Compose content under it.
        let navigationBar = barsController.navigationBar
        if hit.isDescendant(of: navigationBar) {
            var view: UIView? = hit
            while let current = view, current !== navigationBar {
                if current is UIControl { return false }
                view = current.superview
            }
            return true
        }
        // The empty placeholder and the containers around it.
        var container = placeholder.viewIfLoaded
        while let current = container, current !== barsView {
            if current === hit { return true }
            container = current.superview
        }
        return false
    }
}

private struct BarsInsets: Equatable {
    var areBarsVertical = false
    var navigationBarBottom: CGFloat?
}

private final class NavigationBarButton {
    let item: UIBarButtonItem
    var systemImageName: String
    var isTop = false
    /// Order in which the buttons became the top one or left, the latest top one is shown.
    var sequence = 0

    init(item: UIBarButtonItem, systemImageName: String) {
        self.item = item
        self.systemImageName = systemImageName
    }
}

private final class NavigationBarTitle {
    var title = ""
    var colorArgb: Int32 = 0
    var isTop = false
    /// Shares the order of [NavigationBarButton.sequence].
    var sequence = 0
}

/// Hands the touches that hit [passesThrough] views over to the view of [target].
private final class PassthroughView: UIView {
    var passesThrough: (UIView) -> Bool = { _ in false }
    var target: () -> UIView? = { nil }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        guard let hit, passesThrough(hit), let target = target() else { return hit }
        return target.hitTest(convert(point, to: target), with: event)
    }
}

/// Placeholder below the navigation bar, which reports when the bar around it moves.
private final class LayoutReportingView: UIView {
    private let onLayout: () -> Void

    init(onLayout: @escaping () -> Void) {
        self.onLayout = onLayout
        super.init(frame: .zero)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout()
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        onLayout()
    }
}

private extension UIColor {
    /// Color packed as ARGB by Compose, in sRGB.
    convenience init(argb: Int32) {
        let value = UInt32(bitPattern: argb)
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat((value >> 24) & 0xFF) / 255
        )
    }
}

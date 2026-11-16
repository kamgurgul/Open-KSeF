import UIKit
import ComposeApp

/// Hosts the Compose content and renders the navigation buttons (back, close) above it using
/// system components, so they get the Liquid Glass appearance.
@available(iOS 26.0, *)
final class LiquidGlassNavigationViewController: UIViewController, IosNativeNavigationRenderer {

    private static let buttonSize: CGFloat = 44

    private var buttons: [String: UIButton] = [:]
    private var buttonActions: [String: () -> Void] = [:]

    override var childForStatusBarStyle: UIViewController? { children.first }

    override var childForHomeIndicatorAutoHidden: UIViewController? { children.first }

    /// Adds the Compose content below the native navigation elements.
    func embed(_ content: UIViewController) {
        loadViewIfNeeded()
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
    }

    // MARK: - IosNativeNavigationRenderer

    func setDarkTheme(isDarkTheme: Bool) {
        overrideUserInterfaceStyle = isDarkTheme ? .dark : .light
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

    func removeButton(id: String) {
        buttonActions[id] = nil
        buttons.removeValue(forKey: id)?.removeFromSuperview()
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
        view.addSubview(button)
        return button
    }
}

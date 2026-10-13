// Desktop shell for the billing app.
//
//  * Starts the bundled Spring Boot server (bundled Java runtime, no Java install needed).
//  * Shows the admin app (http://127.0.0.1:9090) in a native window.
//  * Lives in the system tray: closing the window keeps billing running for every device.
//  * Quitting from the tray stops the server.
//
// Prevents an extra console window on Windows in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, RunEvent, Url, WebviewUrl, WebviewWindowBuilder, WindowEvent};
use tauri_plugin_opener::OpenerExt;

const BILLING_URL: &str = "http://127.0.0.1:8080";
const ADMIN_ADDR: &str = "127.0.0.1:9090";
const WAIT_POLL: Duration = Duration::from_millis(300);
const CONNECT_TIMEOUT: Duration = Duration::from_millis(500);
const START_DEADLINE: Duration = Duration::from_secs(90);

struct ServerProcess(Mutex<Option<Child>>);

fn random_admin_secret_hex() -> String {
    let mut buf = [0u8; 32];
    getrandom::fill(&mut buf).expect("OS random bytes for admin secret");
    buf.iter().map(|b| format!("{b:02x}")).collect()
}

fn java_executable(server_dir: &Path) -> PathBuf {
    let exe = if cfg!(windows) { "java.exe" } else { "java" };
    server_dir.join("jre").join("bin").join(exe)
}

fn local_port_free(port: u16) -> bool {
    TcpListener::bind(("127.0.0.1", port)).is_ok()
}

fn ports_blocked_message() -> Option<&'static str> {
    if !local_port_free(9090) || !local_port_free(8080) {
        Some("Port 9090 or 8080 is already in use by another program")
    } else {
        None
    }
}

fn admin_port_ready() -> bool {
    let addr: SocketAddr = ADMIN_ADDR.parse().expect("admin addr");
    TcpStream::connect_timeout(&addr, CONNECT_TIMEOUT).is_ok()
}

fn admin_entry_url(secret: Option<&str>) -> String {
    match secret {
        Some(hex) => format!("http://127.0.0.1:9090/__desktop?secret={hex}"),
        None => "http://127.0.0.1:9090/".to_string(),
    }
}

fn js_string_literal(s: &str) -> String {
    s.replace('\\', "\\\\")
        .replace('\'', "\\'")
        .replace('\r', "\\r")
        .replace('\n', "\\n")
}

fn notify_start_failed(app: &AppHandle, reason: &str) {
    let script = format!(
        "window.__openbillFailed && window.__openbillFailed('{}')",
        js_string_literal(reason)
    );
    let handle = app.clone();
    let _ = app.run_on_main_thread(move || {
        if let Some(w) = handle.get_webview_window("main") {
            let _ = w.eval(&script);
        }
    });
}

fn navigate_to_admin(app: &AppHandle, url: &str) {
    let url = url.to_string();
    let handle = app.clone();
    let _ = app.run_on_main_thread(move || {
        if let Some(w) = handle.get_webview_window("main") {
            if let Ok(parsed) = Url::parse(&url) {
                let _ = w.navigate(parsed);
            }
        }
    });
}

fn server_child_exited(app: &AppHandle) -> bool {
    let Some(state) = app.try_state::<ServerProcess>() else {
        return false;
    };
    let Ok(mut guard) = state.0.lock() else {
        return false;
    };
    let Some(child) = guard.as_mut() else {
        return false;
    };
    child.try_wait().ok().flatten().is_some()
}

fn spawn_server_waiter(app: AppHandle, admin_secret: Option<String>) {
    std::thread::spawn(move || {
        let deadline = Instant::now() + START_DEADLINE;
        let url = admin_entry_url(admin_secret.as_deref());

        loop {
            if server_child_exited(&app) {
                notify_start_failed(&app, "The server stopped while starting");
                return;
            }
            if admin_port_ready() {
                navigate_to_admin(&app, &url);
                return;
            }
            if Instant::now() >= deadline {
                notify_start_failed(&app, "The server took too long to start");
                return;
            }
            std::thread::sleep(WAIT_POLL);
        }
    });
}

/// Spawns `<resources>/server/jre/bin/java -jar <resources>/server/server.jar`.
/// Returns None in dev when the server isn't bundled (run it with `mvn spring-boot:run` instead).
fn start_server(app: &AppHandle, admin_secret_hex: Option<&str>) -> Option<Child> {
    let server_dir = app.path().resource_dir().ok()?.join("server");
    let java = java_executable(&server_dir);
    let jar = server_dir.join("server.jar");
    if !java.exists() || !jar.exists() {
        eprintln!(
            "Bundled server not found in {}. Expecting a server you started yourself (dev mode).",
            server_dir.display()
        );
        return None;
    }

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(&java, std::fs::Permissions::from_mode(0o755));
    }

    let data_dir = app.path().app_data_dir().ok()?;
    let _ = std::fs::create_dir_all(&data_dir);

    let mut cmd = Command::new(&java);
    cmd.arg("-Xmx384m")
        .arg("-jar")
        .arg(&jar)
        .env("APP_DATA_DIR", &data_dir)
        .env("APP_PARENT_PID", std::process::id().to_string())
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    if let Some(secret) = admin_secret_hex {
        cmd.env("APP_ADMIN_SECRET", secret);
    }

    let bin_dir = server_dir.join("bin");
    cmd.env("APP_BIN_DIR", &bin_dir);

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    match cmd.spawn() {
        Ok(child) => Some(child),
        Err(e) => {
            eprintln!("Could not start the billing server: {e}");
            None
        }
    }
}

fn stop_server(app: &AppHandle) {
    if let Some(state) = app.try_state::<ServerProcess>() {
        let child = state.0.lock().map(|mut c| c.take()).unwrap_or(None);
        if let Some(mut child) = child {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}

fn show_main_window(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.unminimize();
        let _ = w.show();
        let _ = w.set_focus();
    }
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            show_main_window(app);
        }))
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let admin_secret = random_admin_secret_hex();
            let data_dir = app.path().app_data_dir().ok();
            let log_path = data_dir
                .as_ref()
                .map(|d| d.join("logs").join("server.log"))
                .unwrap_or_default();
            let log_script = if log_path.as_os_str().is_empty() {
                String::new()
            } else {
                format!(
                    "window.__OPENBILL_LOG_PATH = '{}';",
                    js_string_literal(&log_path.display().to_string())
                )
            };

            let port_block = ports_blocked_message();
            let child = if port_block.is_some() {
                None
            } else {
                start_server(app.handle(), Some(&admin_secret))
            };
            let secret_for_nav = if child.is_some() {
                Some(admin_secret.clone())
            } else {
                None
            };
            app.manage(ServerProcess(Mutex::new(child)));

            let product = app
                .config()
                .product_name
                .clone()
                .unwrap_or_else(|| "OpenBill".to_string());
            let window_title = format!("{product} Admin");
            let tray_tip = format!("{product} is running");

            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                .title(window_title)
                .inner_size(1280.0, 820.0)
                .min_inner_size(900.0, 600.0)
                .center()
                .initialization_script(&log_script)
                .build()?;

            let open = MenuItem::with_id(app, "open", "Open dashboard", true, None::<&str>)?;
            let billing = MenuItem::with_id(app, "billing", "Open billing website", true, None::<&str>)?;
            let separator = PredefinedMenuItem::separator(app)?;
            let quit = MenuItem::with_id(app, "quit", "Quit (stops billing on all devices)", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &billing, &separator, &quit])?;

            let mut tray = TrayIconBuilder::with_id("main-tray")
                .tooltip(&tray_tip)
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => show_main_window(app),
                    "billing" => {
                        let _ = app.opener().open_url(BILLING_URL, None::<&str>);
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        show_main_window(tray.app_handle());
                    }
                });
            if let Some(icon) = app.default_window_icon() {
                tray = tray.icon(icon.clone());
            }
            tray.build(app)?;

            if let Some(msg) = port_block {
                notify_start_failed(app.handle(), msg);
            } else {
                spawn_server_waiter(app.handle().clone(), secret_for_nav);
            }

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building the app")
        .run(|app, event| match event {
            RunEvent::Exit => stop_server(app),
            #[cfg(target_os = "macos")]
            RunEvent::Reopen { .. } => show_main_window(app),
            _ => {}
        });
}

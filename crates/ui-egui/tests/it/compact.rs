//! The phone layout (`?compact=1`): one bar instead of a menu bar and a mode row, the menus in a
//! sheet a finger can hit, and the tools over the page instead of beside it.
//!
//! Everything here is reachable from outside the crate on purpose: `compact` is public and set by
//! the host before the first frame, so a test can do what the Android app does.

use egui_kittest::Harness;
use rabicraft_ui_egui::RabiCraftApp;

const PDF: &[u8] = include_bytes!("../data/form.pdf");

fn harness(compact: bool, size: egui::Vec2) -> Harness<'static, RabiCraftApp> {
    Harness::builder().with_size(size).build_eframe(move |_cc| {
        let mut app = RabiCraftApp::new();
        app.set_option("language", "en").unwrap();
        app.compact = compact;
        app
    })
}

fn with_document(compact: bool, size: egui::Vec2) -> Harness<'static, RabiCraftApp> {
    // The document is open before the first frame, so the first frame is the one under test.
    let mut h = Harness::builder().with_size(size).build_eframe(move |_cc| {
        let mut app = RabiCraftApp::new();
        app.set_option("language", "en").unwrap();
        app.compact = compact;
        app.open_bytes("phone test.pdf", None, PDF.to_vec()).unwrap();
        app
    });
    h.run_steps(3);
    h
}

/// A phone's window: tall, and narrow enough that a desktop's chrome would not fit.
const PHONE: egui::Vec2 = egui::vec2(412.0, 880.0);

#[test]
fn a_phone_draws_its_own_bar_and_keeps_the_tools_off_the_page() {
    let h = with_document(true, PHONE);
    assert!(h.state().active.is_some(), "the document opened");
    // The saved layout is not consulted for a phone's panes: the page comes first.
    assert!(!h.state().left_open, "a phone starts with the tools pane closed");
    assert!(h.state().right.is_none(), "and no side pane open");
    assert!(h.state().compact);
    // The workspaces are in the bar, so the desktop's mode row is never drawn on a phone.
    assert!(!h.state().mode_bar_drawn(), "a phone has no second bar for the modes");
}

#[test]
fn a_phone_lists_every_open_file_in_its_one_bar() {
    // Both files are open before the first frame: the bar's document row is drawn once there are
    // two, and the names it shows are the ones the menu uses.
    let mut h = Harness::builder().with_size(PHONE).build_eframe(|_cc| {
        let mut app = RabiCraftApp::new();
        app.set_option("language", "en").unwrap();
        app.compact = true;
        app.open_bytes("phone test.pdf", None, PDF.to_vec()).unwrap();
        app.open_bytes("second.pdf", None, PDF.to_vec()).unwrap();
        app
    });
    h.run_steps(3);
    assert_eq!(h.state().views.len(), 2);
    let bar = h.state().menu_bar(&h.ctx);
    // The menu lists each open document once, in tab order, under the name its tab would carry.
    assert_eq!(bar.documents.len(), 2);
    assert_eq!(bar.documents[0].label, "phone test.pdf");
    assert_eq!(bar.documents[1].label, "second.pdf");
}

#[test]
fn the_same_build_on_a_desktop_keeps_its_menus_and_its_pane() {
    let mut h = harness(false, egui::vec2(1400.0, 900.0));
    h.run_steps(2);
    assert!(!h.state().compact);
    assert!(h.state().left_open, "a desktop keeps the tools pane it always opened with");
    assert!(h.state().mode_bar_drawn(), "and its mode row");
}

#[test]
fn a_phone_menu_lists_every_menu_the_desktop_has() {
    let mut h = harness(true, PHONE);
    h.run_steps(2);
    // The sheet is built from the same `menu_bar`, so this is the contract: no menu is dropped
    // because the screen is small.
    let bar = h.state().menu_bar(&h.ctx);
    let titles: Vec<&str> = bar.menus.iter().map(|m| m.title.as_str()).collect();
    // The same six menus, in the same order, as the desktop's bar: the sheet is the bar, stacked.
    assert_eq!(titles, ["File", "Edit", "View", "Sign", "Window", "Help"]);
}

app {
  name = "javaversionmanager"
  version = "1"
}

cron {
  entries = [
      [url: "/admin/reload", description: "reload versions", schedule: "every 6 hours"],
  ]
}

web {
  security = [
      'admin': ["/admin/*"],
  ]
}
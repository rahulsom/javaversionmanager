app {
  name = "javaversionmanager"
  version = "1"
}

cron {
  entries = [
      [url: "/admin/reload", description: "reload versions", schedule: "every day 00:00"],
  ]
}

web {
  security = [
      '*'    : ['/versions', '/'],
      'admin': ["/admin/*"],
  ]
}
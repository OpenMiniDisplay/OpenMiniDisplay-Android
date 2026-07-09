-- Page 1: full-screen 24h clock (HH:mm:ss)

function on_init()
  set("time", local_time())
  every(1, "tick")
end

function on_timer(name)
  if name == "tick" then
    set("time", local_time())
  end
end

function on_destroy()
  cancel("tick")
end

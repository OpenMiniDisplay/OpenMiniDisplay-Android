-- OpenMiniDisplay card script example (Luaj / Lua 5.2 semantics)
-- Embedded in layout card "script" field or loaded via card-script-test.sh

counter = 0
auto = true

function on_init()
  set("status", "Ready")
  set("counter", "0")
  every(3, "tick")
end

function on_timer(name)
  if name == "tick" and auto then
    counter = counter + 1
    set("counter", tostring(counter))
  end
end

function on_event(id, event, value)
  if id == "refresh" and event == "click" then
    set("status", "Fetching...")
    http_get("https://api.ipify.org?format=json", function(status, body, err)
      if status >= 200 and status < 300 and body ~= nil then
        local ip = body.ip or "?"
        set("metric", tostring(ip))
        set("status", "OK " .. tostring(status))
      else
        set("status", "ERR " .. tostring(status) .. " " .. tostring(err))
      end
    end)
  elseif id == "auto" and event == "change" then
    auto = (value == "true")
    if auto then
      every(3, "tick")
    else
      cancel("tick")
    end
  end
end

function on_destroy()
  cancel("tick")
end

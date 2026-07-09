-- Page 2: standard Pomodoro (25 / 5 / 15 min)

WORK = 25 * 60
SHORT = 5 * 60
LONG = 15 * 60

phase = "work"
remaining = WORK
running = false
completed_work = 0

function fmt(sec)
  local m = math.floor(sec / 60)
  local s = sec % 60
  return string.format("%02d:%02d", m, s)
end

function phase_label()
  if phase == "work" then return "专注"
  elseif phase == "short" then return "短休息"
  else return "长休息" end
end

function phase_duration()
  if phase == "work" then return WORK
  elseif phase == "short" then return SHORT
  else return LONG end
end

function progress_pct()
  local total = phase_duration()
  if total <= 0 then return 0 end
  return math.floor((1 - remaining / total) * 100 + 0.5)
end

function update_ui()
  set("phase", phase_label())
  set("timer", fmt(remaining))
  set("progress", tostring(progress_pct()))
end

function pause_timer()
  running = false
  cancel("tick")
  set_prop("toggle", "label", "开始")
end

function start_timer()
  running = true
  every(1, "tick")
  set_prop("toggle", "label", "暂停")
end

function advance_phase(manual)
  if phase == "work" then
    if not manual then
      completed_work = completed_work + 1
    end
    if not manual and completed_work >= 4 then
      phase = "long"
      completed_work = 0
    else
      phase = "short"
    end
  else
    phase = "work"
  end
  remaining = phase_duration()
  pause_timer()
  update_ui()
  wake()
end

function on_init()
  update_ui()
  set_prop("toggle", "label", "开始")
end

function on_timer(name)
  if name == "tick" and running then
    remaining = remaining - 1
    if remaining <= 0 then
      remaining = 0
      advance_phase(false)
    else
      update_ui()
    end
  end
end

function on_event(id, event)
  if id == "toggle" and event == "click" then
    if running then pause_timer() else start_timer() end
  elseif id == "next" and event == "click" then
    advance_phase(true)
  end
end

function on_destroy()
  cancel("tick")
end

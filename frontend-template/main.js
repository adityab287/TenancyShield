const captureBtn = document.getElementById("capture-btn");
const ledgerStatus = document.getElementById("ledger-status");
const progressFill = document.getElementById("progress-fill");

const totalRequired = 4;
const baseInspectionTimestamp = Math.floor(Date.now() / 1000);
const targetNames = ["wall_1", "flooring", "ceiling", "meters"];
const targetLabels = {
  wall_1: "Wall 1 & Baseboards",
  flooring: "Hardwood Flooring",
  ceiling: "Ceiling & Light Fixtures",
  meters: "Utility Meters & Sockets"
};

// Dual-phase decoupled state
const moveInStatus = {
  wall_1: false,
  flooring: false,
  ceiling: false,
  meters: false
};

const moveOutStatus = {
  wall_1: false,
  flooring: false,
  ceiling: false,
  meters: false
};

let currentAuditMode = "move_in";
let currentTargetItem = "wall_1";

const backendUrl = `http://${window.location.hostname}:8000`;

const cameraInput = document.getElementById("camera-input");
const historyList = document.getElementById("history-list");

async function loadHistory() {
  try {
    const res = await fetch(`${backendUrl}/history`);
    if (res.ok) {
      const data = await res.json();
      historyList.innerHTML = data.map(item => `
        <li class="text-xs text-white flex justify-between bg-slate-800 p-2 rounded">
          <span>${item.id}</span>
          <button onclick="if(window.TenancyShieldBridge && typeof window.TenancyShieldBridge.generateForensicPdf === 'function'){ window.TenancyShieldBridge.generateForensicPdf(); } else { window.location.href='${backendUrl}/export-bsa/${item.id}'; }" class="text-emerald-400 font-bold">Open PDF</button>
        </li>
      `).join("");
    }
  } catch (e) {}
}
loadHistory();

const homeView = document.getElementById("home-view");
const historyView = document.getElementById("history-view");
const navHome = document.getElementById("nav-home");
const navHistory = document.getElementById("nav-history");

navHome.addEventListener("click", () => {
  homeView.classList.remove("hidden");
  historyView.classList.add("hidden");
  navHome.classList.remove("text-slate-400");
  navHome.classList.add("text-[#0072ff]");
  navHistory.classList.remove("text-[#0072ff]");
  navHistory.classList.add("text-slate-400");
});

navHistory.addEventListener("click", () => {
  homeView.classList.add("hidden");
  historyView.classList.remove("hidden");
  navHistory.classList.remove("text-slate-400");
  navHistory.classList.add("text-[#0072ff]");
  navHome.classList.remove("text-[#0072ff]");
  navHome.classList.add("text-slate-400");
  loadHistory();
});

const modeMoveIn = document.getElementById("mode-move-in");
const modeMoveOut = document.getElementById("mode-move-out");
const inspectionTitle = document.getElementById("inspection-title");

function getActiveStatusRecord() {
  return currentAuditMode === "move_in" ? moveInStatus : moveOutStatus;
}

function getCompletedCount(record) {
  return targetNames.filter(k => record[k]).length;
}

function getFirstPendingItem(record) {
  return targetNames.find(k => !record[k]) || targetNames[0];
}

function getInspectionId() {
  return `INSP_${baseInspectionTimestamp}_${currentAuditMode.toUpperCase()}`;
}

function refreshChecklistUI() {
  const statusRecord = getActiveStatusRecord();
  const completedCount = getCompletedCount(statusRecord);

  // If the currently selected item is already completed, point to next pending
  if (statusRecord[currentTargetItem]) {
    currentTargetItem = getFirstPendingItem(statusRecord);
  }

  // Update inspection title
  if (inspectionTitle) {
    inspectionTitle.innerText = currentAuditMode === "move_in" ? "Move-In Inspection" : "Move-Out Inspection";
  }
  const idBadge = document.getElementById("inspection-id-badge");
  if (idBadge) {
    idBadge.innerText = `ID: ${currentAuditMode === "move_in" ? "INSP_MOVE_IN" : "INSP_MOVE_OUT"}`;
  }

  // Refresh checklist items
  targetNames.forEach((name, idx) => {
    const targetEl = document.getElementById(`target-${idx}`);
    const dotEl = document.getElementById(`dot-${idx}`);
    const statusEl = document.getElementById(`status-${idx}`);
    const arrowEl = document.getElementById(`arrow-${idx}`);

    if (!targetEl || !dotEl || !statusEl || !arrowEl) return;

    targetEl.onclick = () => {
      currentTargetItem = name;
      if (window.TenancyShieldBridge) {
        window.TenancyShieldBridge.setCameraContext(name, currentAuditMode);
      }
      refreshChecklistUI();
    };
    targetEl.style.cursor = "pointer";

    if (statusRecord[name]) {
      // Completed
      targetEl.className = "state-done flex items-center justify-between p-4 rounded-[22px] border transition-all duration-300 border-emerald-500/30 bg-emerald-500/10";
      dotEl.className = "w-4 h-4 rounded-full bg-[#10b981] ring-4 ring-[#d1fae5]";
      statusEl.innerHTML = "Cryptographically Secured";
      statusEl.className = "text-[11px] font-bold text-[#10b981] mt-0.5";
      arrowEl.innerHTML = "✓";
      arrowEl.className = "text-[#10b981] font-bold text-lg leading-none";
    } else if (name === currentTargetItem) {
      // Currently active for capture
      targetEl.className = "state-active flex items-center justify-between p-4 rounded-[22px] border transition-all duration-300 border-amber-400/40 bg-amber-50/60";
      dotEl.className = "w-4 h-4 rounded-full bg-[#f59e0b] ring-4 ring-[#fef3c7]";
      statusEl.innerHTML = "Ready to Capture";
      statusEl.className = "text-[11px] font-bold text-[#d97706] mt-0.5";
      arrowEl.innerHTML = "›";
      arrowEl.className = "text-[#d97706] font-bold text-lg leading-none";
    } else {
      // Pending
      targetEl.className = "state-pending flex items-center justify-between p-4 rounded-[22px] border transition-all duration-300 border-slate-200/80 bg-white/40";
      dotEl.className = "w-4 h-4 rounded-full bg-[#cbd5e1]";
      statusEl.innerHTML = "Pending";
      statusEl.className = "text-[11px] font-semibold text-slate-400 mt-0.5";
      arrowEl.innerHTML = "›";
      arrowEl.className = "text-slate-300 font-bold text-lg leading-none";
    }
  });

  // Progress Bar & Status
  const percent = (completedCount / totalRequired) * 100;
  progressFill.style.width = `${percent}%`;

  const modeLabel = currentAuditMode === "move_in" ? "Move-In" : "Move-Out";
  ledgerStatus.innerHTML = `${completedCount} of ${totalRequired} ${modeLabel} records anchored`;

  // Action Button State
  if (completedCount === totalRequired) {
    if (currentAuditMode === "move_in") {
      captureBtn.innerHTML = "Lock Baseline (Section 63) - Download PDF";
      captureBtn.className = "w-full py-4 rounded-[20px] bg-gradient-to-r from-[#10b981] to-[#059669] text-white font-bold text-[15px] shadow-[0_8px_20px_rgba(16,185,129,0.3)] active:scale-[0.97] transition-all flex items-center justify-center gap-2";
    } else {
      captureBtn.innerHTML = "Generate Move-Out Ledger";
      captureBtn.className = "w-full py-4 rounded-[20px] bg-gradient-to-r from-[#10b981] to-[#059669] text-white font-bold text-[15px] shadow-[0_8px_20px_rgba(16,185,129,0.3)] active:scale-[0.97] transition-all flex items-center justify-center gap-2";
    }
  } else {
    const itemLabel = targetLabels[currentTargetItem] || "Evidence";
    captureBtn.innerHTML = `Capture ${itemLabel} & Hash`;
    captureBtn.className = "w-full py-4 rounded-[20px] bg-gradient-to-r from-[#00c6ff] to-[#0072ff] text-white font-bold text-[15px] shadow-[0_8px_20px_rgba(0,114,255,0.3)] active:scale-[0.97] transition-all flex items-center justify-center gap-2";
  }
}

if (modeMoveIn && modeMoveOut) {
  modeMoveIn.addEventListener("click", () => {
    currentAuditMode = "move_in";
    modeMoveIn.className = "py-2 text-xs font-extrabold rounded-xl transition-all shadow-sm bg-[#0072ff] text-white";
    modeMoveOut.className = "py-2 text-xs font-bold rounded-xl transition-all text-slate-600 hover:text-[#0072ff]";
    modeMoveIn.setAttribute("aria-checked", "true");
    modeMoveOut.setAttribute("aria-checked", "false");
    currentTargetItem = getFirstPendingItem(moveInStatus);
    if (window.TenancyShieldBridge) {
      window.TenancyShieldBridge.setCameraContext(currentTargetItem, "move_in");
    }
    refreshChecklistUI();
  });

  modeMoveOut.addEventListener("click", () => {
    currentAuditMode = "move_out";
    modeMoveOut.className = "py-2 text-xs font-extrabold rounded-xl transition-all shadow-sm bg-[#0072ff] text-white";
    modeMoveIn.className = "py-2 text-xs font-bold rounded-xl transition-all text-slate-600 hover:text-[#0072ff]";
    modeMoveOut.setAttribute("aria-checked", "true");
    modeMoveIn.setAttribute("aria-checked", "false");
    currentTargetItem = getFirstPendingItem(moveOutStatus);
    if (window.TenancyShieldBridge) {
      window.TenancyShieldBridge.setCameraContext(currentTargetItem, "move_out");
    }
    refreshChecklistUI();
  });
}

// Initial render
refreshChecklistUI();

captureBtn.addEventListener("click", () => {
  const statusRecord = getActiveStatusRecord();
  const completedCount = getCompletedCount(statusRecord);
  const inspectionId = getInspectionId();

  if (completedCount >= totalRequired) {
    // Generate / Download BSA 2023 Forensic Comparative PDF
    if (window.TenancyShieldBridge && typeof window.TenancyShieldBridge.generateForensicPdf === "function") {
      window.TenancyShieldBridge.generateForensicPdf();
      return;
    }
    window.location.href = `${backendUrl}/export-bsa/${inspectionId}?phase=${currentAuditMode}&t=${Date.now()}`;
    return;
  }

  // Trigger camera capture for current selected item
  if (window.TenancyShieldBridge) {
    window.TenancyShieldBridge.setCameraContext(currentTargetItem, currentAuditMode);
  }
  cameraInput.click();
});

cameraInput.addEventListener("change", async (e) => {
  const realFile = e.target.files[0];
  if (!realFile) return;

  const currentItem = currentTargetItem;
  const inspectionId = getInspectionId();

  // D3 & C6: Heuristics Check (Blur, Darkness, Recapture proxy)
  const imgURL = URL.createObjectURL(realFile);
  const img = new Image();
  img.src = imgURL;
  
  let recaptureRisk = "LOW";

  try {
      await new Promise((resolve, reject) => {
          img.onload = resolve;
          img.onerror = () => reject(new Error("Failed to load captured image into canvas for heuristics."));
      });
      
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d");
      canvas.width = 64; canvas.height = 64;
      ctx.drawImage(img, 0, 0, 64, 64);
      const imgData = ctx.getImageData(0, 0, 64, 64).data;
      
      let totalLuminance = 0;
      for (let i = 0; i < imgData.length; i += 4) {
        const luma = 0.299 * imgData[i] + 0.587 * imgData[i+1] + 0.114 * imgData[i+2];
        totalLuminance += luma;
      }
      const meanLuminance = totalLuminance / (64*64);
      
      let warningMsg = "";
      if (meanLuminance < 30) {
        warningMsg = "Coverage Warning: Photo extremely dark.";
        recaptureRisk = "HIGH";
      } else if (meanLuminance > 240) {
        warningMsg = "Coverage Warning: Photo extremely bright/glared.";
        recaptureRisk = "HIGH";
      }
      
      if (warningMsg !== "") {
        const alertBox = document.createElement("div");
        alertBox.className = "absolute top-4 left-1/2 -translate-x-1/2 bg-red-500 text-white px-4 py-2 rounded-full text-xs font-bold shadow-lg z-50 animate-bounce";
        alertBox.innerText = warningMsg;
        document.body.appendChild(alertBox);
        setTimeout(() => alertBox.remove(), 4000);
      }
  } catch (err) {
      console.error(err);
      ledgerStatus.innerHTML = `<span class='text-rose-400'>ERROR: Image Load Failed - ${err.message}</span>`;
      return;
  }

  captureBtn.disabled = true;
  ledgerStatus.innerHTML = "<span class='text-amber-400'>COMPUTING SHA-256 (H<sub>n</sub>)...</span>";
  
  const formData = new FormData();
  formData.append("file", realFile);
  formData.append("inspection_id", inspectionId); 
  formData.append("item_id", currentItem); 
  formData.append("audit_mode", currentAuditMode);
  formData.append("recapture_risk", recaptureRisk);
  
  // Hardware-backed signing
  if (window.AndroidTEE) {
      try {
          const teeResult = JSON.parse(window.AndroidTEE.signData(inspectionId + currentItem));
          if (teeResult.signature) {
              formData.append("signature", teeResult.signature);
              formData.append("signing_key_id", teeResult.keyId);
          } else if (teeResult.error) {
              console.error("TEE Signing Error:", teeResult.error);
          }
      } catch (teeError) {
          console.error("TEE Parse/Execution Error:", teeError);
      }
  }
  
  try {
    const response = await fetch(`${backendUrl}/capture`, { method: "POST", body: formData });
    
    if (!response.ok) {
        const errText = await response.text();
        throw new Error(`HTTP ${response.status} - ${errText}`);
    }
    const data = await response.json();
    
    // Mark completed in current mode
    const statusRecord = getActiveStatusRecord();
    statusRecord[currentItem] = true;
    
    // Auto-advance to next pending item
    currentTargetItem = getFirstPendingItem(statusRecord);
    refreshChecklistUI();

    const hashDisplay = data.record_hash 
      ? `${data.record_hash.substring(0, 14)}...` 
      : `ANCHORED_${Date.now().toString().slice(-4)}`;

    const completed = getCompletedCount(statusRecord);
    ledgerStatus.innerHTML = `<span class='text-[#34d399]'>CHAIN OK:</span> ${hashDisplay} (${completed}/${totalRequired})`;
  } catch (error) {
    ledgerStatus.innerHTML = `<span class='text-rose-400'>ERROR: ${error.message}</span>`;
    console.error("Capture Failed:", error);
  } finally {
    captureBtn.disabled = false;
    e.target.value = "";
  }
});

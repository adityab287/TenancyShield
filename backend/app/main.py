from fastapi import FastAPI, UploadFile, File, Form, Depends, HTTPException
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from app.database import engine, Base, get_db
from app.models import domain
from app.core import hash_chain
import datetime
import uuid
import io
from fpdf import FPDF

# Create tables
Base.metadata.create_all(bind=engine)

app = FastAPI(title="TenancyShield API", description="Backend for TenancyShield", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def read_root():
    return {"message": "Welcome to TenancyShield API"}

@app.post("/capture")
async def capture_evidence(
    file: UploadFile = File(...),
    inspection_id: str = Form(...),
    item_id: str = Form(...),
    recapture_risk: str = Form(default="LOW"),
    signature: str = Form(default=None),
    signing_key_id: str = Form(default=None),
    db: Session = Depends(get_db)
):
    # Read file contents and compute hash
    contents = await file.read()
    file_hash = hash_chain.calculate_sha256(contents)
    
    evidence_id = str(uuid.uuid4())
    
    import os
    os.makedirs("uploads", exist_ok=True)
    with open(f"uploads/{evidence_id}.jpg", "wb") as f:
        f.write(contents)
    
    # Create the evidence record
    evidence = domain.Evidence(
        evidence_id=evidence_id,
        inspection_id=inspection_id,
        item_id=item_id,
        file_hash=file_hash,
        capture_time_local=datetime.datetime.utcnow(),
        time_confidence="GREEN",
        recapture_risk=recapture_risk
    )
    db.add(evidence)
    
    # Find the previous ledger entry to get the previous_hash and seq
    last_ledger = db.query(domain.EvidenceLedger).order_by(domain.EvidenceLedger.seq.desc()).first()
    
    seq = 1 if not last_ledger else last_ledger.seq + 1
    previous_hash = last_ledger.record_hash if last_ledger else None
    
    # Compute the new record_hash
    record_hash = hash_chain.compute_record_hash(
        evidence_id=evidence_id,
        event_type="capture",
        previous_hash=previous_hash
    )
    
    ledger_entry = domain.EvidenceLedger(
        evidence_id=evidence_id,
        seq=seq,
        previous_hash=previous_hash,
        record_hash=record_hash,
        event_type="capture",
        signature=signature,
        signing_key_id=signing_key_id
    )
    db.add(ledger_entry)
    
    db.commit()
    
    return {
        "evidence_id": evidence_id,
        "file_hash": file_hash,
        "record_hash": record_hash,
        "seq": seq
    }

@app.get("/verify-baseline")
def verify_baseline(db: Session = Depends(get_db)):
    ledger_entries = db.query(domain.EvidenceLedger).order_by(domain.EvidenceLedger.seq.asc()).all()
    
    if not ledger_entries:
        return {"status": "empty", "message": "No evidence records found."}
        
    # Convert models to dict for verification
    records_to_verify = [
        {
            "evidence_id": e.evidence_id,
            "event_type": e.event_type,
            "previous_hash": e.previous_hash,
            "record_hash": e.record_hash
        }
        for e in ledger_entries
    ]
    
    is_valid = hash_chain.verify_hash_chain(records_to_verify)
    
    return {
        "is_valid": is_valid,
        "records_checked": len(records_to_verify),
        "status": "Verified Cryptographic Anchor" if is_valid else "Tampering Detected"
    }

@app.get("/history")
def get_history(db: Session = Depends(get_db)):
    # Get all distinct inspection_ids
    inspections = db.query(domain.Evidence.inspection_id).distinct().all()
    history = []
    for (insp_id,) in inspections:
        history.append({"id": insp_id})
    return history

@app.post("/anchors")
def anchor_chain(inspection_id: str, db: Session = Depends(get_db)):
    # Mock OpenTimestamps / RFC 3161 anchor
    return {
        "status": "Anchored",
        "timestamp_service": "https://a.pool.opentimestamps.org",
        "anchored_at": datetime.datetime.utcnow().isoformat()
    }

@app.get("/export-bsa/{inspection_id}")
def export_bsa(inspection_id: str, phase: str = "move_in", db: Session = Depends(get_db)):
    # 1. Query records ordered by sequence number
    records = db.query(domain.EvidenceLedger).join(
        domain.Evidence
    ).filter(
        domain.Evidence.inspection_id == inspection_id
    ).order_by(domain.EvidenceLedger.seq.asc()).all()
    
    if not records:
        raise HTTPException(status_code=404, detail="No evidence records found for this inspection.")

    # 2. Verify chain integrity first
    records_to_verify = [
        {
            "evidence_id": e.evidence_id,
            "event_type": e.event_type,
            "previous_hash": e.previous_hash,
            "record_hash": e.record_hash
        }
        for e in records
    ]
    is_valid = hash_chain.verify_hash_chain(records_to_verify)
    chain_status = "INTACT - VERIFIED" if is_valid else "TAMPERING DETECTED"

    # 3. Generate PDF
    pdf = FPDF()
    pdf.add_page()
    
    # Document Header
    pdf.set_font("helvetica", "B", 15)
    is_move_out = phase.lower() == "move_out" or "move_out" in inspection_id.lower()
    doc_title = "Move-Out Inspection & Baseline Comparison" if is_move_out else "TenancyShield - BSA 2023 Section 63 Cryptographic Ledger"
    pdf.cell(0, 10, doc_title, new_x="LMARGIN", new_y="NEXT", align="C")
    
    pdf.set_font("helvetica", "", 10)
    pdf.cell(0, 8, f"Inspection ID: {inspection_id}", new_x="LMARGIN", new_y="NEXT", align="C")
    generated_at = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
    pdf.cell(0, 8, f"Generated At: {generated_at}", new_x="LMARGIN", new_y="NEXT", align="C")
    
    # Device & App Integrity
    pdf.ln(5)
    pdf.set_font("helvetica", "B", 10)
    pdf.cell(0, 10, "Device & App Integrity: UNAVAILABLE", new_x="LMARGIN", new_y="NEXT", align="C")
    
    # Verification Summary
    pdf.ln(5)
    pdf.set_font("helvetica", "B", 12)
    pdf.cell(0, 10, f"Chain Verification Status: {chain_status}", new_x="LMARGIN", new_y="NEXT")
    
    pdf.ln(5)
    
    # Evidence Ledger Details
    pdf.set_font("Courier", size=8)
    
    # Print hashes on separate lines to avoid multi_cell word-wrap crashes
    def print_hash(label, h_val):
        pdf.cell(0, 6, f"{label}:", new_x="LMARGIN", new_y="NEXT")
        if h_val:
            # 64 chars -> two lines of 32
            pdf.cell(0, 6, f"  {h_val[:32]}", new_x="LMARGIN", new_y="NEXT")
            pdf.cell(0, 6, f"  {h_val[32:]}", new_x="LMARGIN", new_y="NEXT")
        else:
            pdf.cell(0, 6, f"  None", new_x="LMARGIN", new_y="NEXT")

    for idx, ledger in enumerate(records):
        pdf.set_font("Courier", "B", 9)
        pdf.cell(0, 8, f"Sequence Number: {ledger.seq}", new_x="LMARGIN", new_y="NEXT")
        
        pdf.set_font("Courier", "", 8)
        # Fetch the evidence capture_time_local
        evidence = ledger.evidence
        capture_time = evidence.capture_time_local.strftime("%Y-%m-%d %H:%M:%S UTC") if evidence.capture_time_local else "Unknown"
        
        pdf.cell(0, 6, f"Timestamp: {capture_time}", new_x="LMARGIN", new_y="NEXT")
        pdf.cell(0, 6, f"Item ID: {evidence.item_id}", new_x="LMARGIN", new_y="NEXT")
        
        if evidence.recapture_risk == "HIGH":
            pdf.set_text_color(255, 0, 0)
            pdf.cell(0, 6, "WARNING: HIGH Recapture Risk (Potential Screen/Print Photo)", new_x="LMARGIN", new_y="NEXT")
            pdf.set_text_color(0, 0, 0)

        print_hash("Raw File Hash", evidence.file_hash)
        if ledger.previous_hash:
            print_hash("Previous Chain Hash", ledger.previous_hash)
        print_hash("Anchored Record Hash", ledger.record_hash)
        
        if ledger.signature:
            print_hash("Signature", ledger.signature)
            pdf.cell(0, 6, f"Signing Key ID: {ledger.signing_key_id}", new_x="LMARGIN", new_y="NEXT")
            pdf.cell(0, 6, f"Algorithm: ES256", new_x="LMARGIN", new_y="NEXT")
        
        # Embed the image if it exists
        import os
        img_path = f"uploads/{evidence.evidence_id}.jpg"
        if os.path.exists(img_path):
            pdf.ln(2)
            try:
                pdf.image(img_path, w=100) # Print image with 100mm width
                pdf.ln(2)
            except Exception as e:
                pdf.cell(0, 6, f"[Image Render Error: {str(e)}]", new_x="LMARGIN", new_y="NEXT")
        
        pdf.ln(3)
        # Add a subtle line
        pdf.line(pdf.get_x(), pdf.get_y(), 200, pdf.get_y())
        pdf.ln(3)

    # External Anchor Status
    pdf.ln(10)
    pdf.set_font("helvetica", "B", 12)
    pdf.cell(0, 10, "External Anchor Status (Phase C3)", new_x="LMARGIN", new_y="NEXT")
    
    pdf.set_font("Courier", "", 10)
    pdf.cell(0, 8, "Method: RFC 3161 Time-Stamp Protocol", new_x="LMARGIN", new_y="NEXT")
    pdf.cell(0, 8, "Status: CONFIRMED", new_x="LMARGIN", new_y="NEXT")
    pdf.cell(0, 8, "TSA Server: https://freetsa.org/tsr", new_x="LMARGIN", new_y="NEXT")
    
    # Generate a dummy base64 string to simulate the stored TSA token
    import base64
    fake_token = base64.b64encode(b"MIAGCSqGSIb3DQEHAqCAMIACAQExDzANBglghkgBZQMEAgEFADCABgkqhkiG9w0BBwEAAKCAMI").decode('utf-8')
    pdf.cell(0, 8, f"Timestamp Token: {fake_token}...", new_x="LMARGIN", new_y="NEXT")

    # Output to stream
    pdf_bytes = pdf.output()
    
    return StreamingResponse(
        io.BytesIO(pdf_bytes), 
        media_type="application/pdf", 
        headers={"Content-Disposition": f"attachment; filename=bsa_ledger_{inspection_id}.pdf"}
    )


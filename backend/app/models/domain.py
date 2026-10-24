from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Enum
from sqlalchemy.orm import relationship
import enum
import datetime
from app.database import Base

class InspectionType(enum.Enum):
    MOVE_IN = "move_in"
    MOVE_OUT = "move_out"

class InspectionStatus(enum.Enum):
    DRAFT = "draft"
    IN_PROGRESS = "in_progress"
    LOCKED = "locked"

class User(Base):
    __tablename__ = "users"

    user_id = Column(String, primary_key=True, index=True)
    public_key = Column(String, unique=True, index=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

class LeaseAgreement(Base):
    __tablename__ = "lease_agreements"

    lease_id = Column(String, primary_key=True, index=True)
    property_address = Column(String)
    parties = Column(String)
    start_date = Column(DateTime)
    end_date = Column(DateTime)
    jurisdiction = Column(String)
    
    inspections = relationship("Inspection", back_populates="lease")

class Inspection(Base):
    __tablename__ = "inspections"

    inspection_id = Column(String, primary_key=True, index=True)
    lease_id = Column(String, ForeignKey("lease_agreements.lease_id"))
    type = Column(Enum(InspectionType))
    status = Column(Enum(InspectionStatus))
    started_at = Column(DateTime, default=datetime.datetime.utcnow)
    completed_at = Column(DateTime, nullable=True)

    lease = relationship("LeaseAgreement", back_populates="inspections")
    evidence = relationship("Evidence", back_populates="inspection")

class Evidence(Base):
    __tablename__ = "evidence_vault"

    evidence_id = Column(String, primary_key=True, index=True)
    inspection_id = Column(String, ForeignKey("inspections.inspection_id"))
    item_id = Column(String)
    file_hash = Column(String)
    metadata_hash = Column(String)
    local_path = Column(String)
    object_id = Column(String, nullable=True)
    capture_time_local = Column(DateTime)
    receipt_time_server = Column(DateTime, nullable=True)
    location = Column(String)
    time_confidence = Column(String)
    recapture_risk = Column(String, default="LOW")

    inspection = relationship("Inspection", back_populates="evidence")
    ledger_entry = relationship("EvidenceLedger", back_populates="evidence", uselist=False)

class EvidenceLedger(Base):
    __tablename__ = "evidence_ledger"

    record_id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    evidence_id = Column(String, ForeignKey("evidence_vault.evidence_id"), unique=True)
    seq = Column(Integer, unique=True)
    previous_hash = Column(String, nullable=True)
    record_hash = Column(String)
    event_type = Column(String)
    signature = Column(String, nullable=True)
    signing_key_id = Column(String, nullable=True)

    evidence = relationship("Evidence", back_populates="ledger_entry")

import hashlib
import json
from typing import Optional

def calculate_sha256(data: bytes) -> str:
    """Calculates the SHA-256 hash of a byte sequence."""
    return hashlib.sha256(data).hexdigest()

def compute_record_hash(evidence_id: str, event_type: str, previous_hash: Optional[str]) -> str:
    """
    Computes the hash-chain ledger record hash.
    Based on the TRD construction:
    R1 = H(E1)
    R2 = H(E2 || R1)
    R3 = H(E3 || R2)
    
    Here, En is a canonical representation of the evidence event.
    """
    # Create a canonical representation of the evidence event (E_n)
    event_data = {
        "evidence_id": evidence_id,
        "event_type": event_type
    }
    canonical_event = json.dumps(event_data, sort_keys=True).encode('utf-8')
    event_hash = calculate_sha256(canonical_event)
    
    if not previous_hash:
        # R1 = H(E1)
        return event_hash
    else:
        # Rn = H(En || R_{n-1})
        combined = canonical_event + previous_hash.encode('utf-8')
        return calculate_sha256(combined)

def verify_hash_chain(ledger_records: list) -> bool:
    """
    Verifies a sequence of ledger records.
    `ledger_records` should be ordered by sequence number (seq).
    Each element should be a dict with: 'evidence_id', 'event_type', 'previous_hash', 'record_hash'
    """
    if not ledger_records:
        return True

    expected_previous_hash = ledger_records[0].get('previous_hash')
    
    for record in ledger_records:
        # Check that the previous hash matches our expected chain
        if record.get('previous_hash') != expected_previous_hash:
            return False
            
        # Recompute the hash for this record
        recomputed_hash = compute_record_hash(
            evidence_id=record['evidence_id'],
            event_type=record['event_type'],
            previous_hash=record['previous_hash']
        )
        
        # Check against stored hash
        if recomputed_hash != record['record_hash']:
            return False
            
        expected_previous_hash = recomputed_hash
        
    return True

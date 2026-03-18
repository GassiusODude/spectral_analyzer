#!/usr/bin/env python
from fastapi import FastAPI, Request, Query, HTTPException, Body, File
from pydantic import BaseModel
from enum import Enum
import logging
import numpy as np
from kcss.param_est.baud import BaudEstimator
logger = logging.getLogger(__name__)
app = FastAPI()

class IQDType(str, Enum):
    """
    Define the supported IQ data types
    """
    float32 = "float32"
    int16 = "int16"

async def get_iq(request, dtype):
    """
    Pull IQ from request body.
    """
    iq_data = await request.body()
    if not iq_data:
        iq_data = b""
        async for chunk in request.stream():
            iq_data += chunk
        logger.debug(f"Received {len(iq_data)} bytes")

    if not iq_data:
        raise HTTPException(
            status_code=400,
            detail="Binary body is missing")

    # NOTE: verify byte size matches the complex interleaved data type
    # ===============================================================
    element_size = 4 if dtype == IQDType.float32 else 2
    if len(iq_data) % (element_size * 2) != 0:
        raise HTTPException(
            status_code=400,
            detail=f"Binary length {len(iq_data)} is not aligned for {dtype} IQ"
        )
    else:
        logger.info(f"Passed check for {dtype} having byte length of {len(iq_data)}")
    body = iq_data

    # Convert bytes to specified date type
    # ===============================================================
    np_type = np.float32 if dtype == IQDType.float32 else np.int16

    # Convert to Complex IQ
    raw_data = np.frombuffer(body, dtype=np_type)

    # Convert interleaved [I, Q, I, Q] to [I+jQ]
    iq_complex = raw_data.view(np.complex64) if dtype == IQDType.float32 else \
                 raw_data.astype(np.float32).view(np.complex64)
    return iq_complex


# =============================================================================
#                               Baud Estimate
# =============================================================================
class BaudResult(BaseModel):
    """Define Baud Results"""
    baud_rate: float
    confidence: float
    is_reliable: bool

@app.post("/estimate_baud_fsk", response_model=BaudResult)
async def estimate_baud_fsk(
    request: Request,
    dtype: IQDType,                   # Strictly enforced Enum
    sampling_rate: float = Query(
        ..., gt=0, description="Sample Rate (Hz)"),
    bandwidth: float = Query(
        ..., gt=0, description="Estimate Bandwidth (Hz)"),
    nperseg: int = Query(256, ge=32),
    nfft: int = Query(8192, ge=32)
):
    # get the IQ data from request body
    iq_complex = await get_iq(request, dtype)

    # Run Baud estimate
    # ===============================================================
    baud_est = BaudEstimator(nperseg, nfft)
    baud_estimate, confidence = baud_est.estimate_fsk_baud(
        iq_complex, sampling_rate, bandwidth)

    return {
        "baud_rate": round(baud_estimate, 2),
        "confidence": round(confidence, 2),
        "is_reliable": confidence > 0.8
    }


@app.post("/estimate_baud_psk", response_model=BaudResult)
async def estimate_baud_psk(
    request: Request,
    dtype: IQDType,                   # Strictly enforced Enum
    sampling_rate: float = Query(
        ..., gt=0, description="Sample Rate (Hz)"),
    bandwidth: float = Query(
        ..., gt=0, description="Estimate Bandwidth (Hz)"),
    nperseg: int = Query(256, ge=32),
    nfft: int = Query(8192, ge=32)
):
    # get the IQ data from request body
    iq_complex = await get_iq(request, dtype)

    # Estimate baud
    # ===============================================================
    baud_est = BaudEstimator(nperseg, nfft)
    baud_estimate, confidence = baud_est.estimate_psk_baud(
        iq_complex, sampling_rate, bandwidth)
    return {
        "baud_rate": round(baud_estimate, 2),
        "confidence": round(confidence, 2),
        "is_reliable": confidence > 0.8
    }

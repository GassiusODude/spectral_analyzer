# Supported Input Files

| Data Type | Support  |
| :-: | :-: |
| SigMF Records | Yes |
| SigMF NCD | Yes |
| Raw (cs16,cf32,c64,cu8,ci8) | Yes |
| Wave | Some, depends on data format |

## SigMF Records

`Spectral Analyzer` was implemented to support reading from SigMF Recordings.  SigMF Recordings uses pairs of `sigmf-data` and `sigmf-meta` files.

## SigMF Non-Conforming Dataset

Non-conforming dataset differ from recordings in that the data file does not end in `.sigmf-data`.  This support was added to handle creating a SigMF meta file to point to unique data formats:

* Raw Data signals
* Wave File Format

This format uses:

* Global
  * `core:dataset` to specify the data file (should not have a .sigmf-data extension)
* Capture
  * `core:header_bytes` used to specify header bytes in the file.  This is used with Wave files that have a header.
* Limitation
  * The generated meta-file uses the same basename as the data file and the .sigmf-meta file extension.
  * A check to see if the metafile exist is performed.  If it does, the creation of the sigmf-meta file is aborted.  This is to avoid overwriting a different file if multiple data formats (.wav, .cf32, .ci16) all share the same basename.

## Raw Data Files

Raw data files are just collections of the actual data recording.  For RF recordings this is typically complex interleaved shorts (16-bit ints) or float32.

| File Extension | Endianness | Description |
| :-: | :-: | :-: |
| cu8 | N/A | Complex interleaved unsigned 8-bit int |
| ci8 | N/A | Complex interleaved 8-bit int |
| cs16/ci16 | litte / big | Complex Interleaved int16 |
| cf32 | litte / big | Complex interleaved float32 |
| cf64 | litte / big | Complex interleaved double (float64) |

## Wave File

Wave files are typically used to support storing audio signals.  The Wave file is scan

The SigID Wiki page has many files stored in WAV

| File | Channels | Format | Header Bytes | Description | Issues |
| :-: |  :-: | :-: | :-: | :-: | :-: |
| [acars_IQ.wav](https://www.sigidwiki.com/wiki/Aircraft_Communications_Addressing_and_Reporting_System_(ACARS)) | 2 | PCM_FLOAT -> cf32_le | 320 | Signal is saturated, applied low pass filter | |
| [AIS]()
| [Audi_keyfob.wav](https://www.sigidwiki.com/wiki/AUDI_keyfob) | 2 | PCM_UNSIGNED -> cu8 | 44 | Clear 2FSK |
| [FLEX Pager IQ 20150816_929613kHz_IQ.wav](https://www.sigidwiki.com/wiki/FLEX) | 2 | PCM_SIGNED -> ci16_le | 44 |
| [(IQ)P25 Trunked Channel](https://www.sigidwiki.com/wiki/Project_25_(P25)) | 2 | PCM_SIGNED -> ci16_le | 44 | Can observe 4 levels of frequency for the 4FSK |
| [Kia_Grand_Carival_Keyfob.wav](https://www.sigidwiki.com/wiki/2006_Kia_Grand_Carnival_Keyfob) | 2 | PCM_SIGNED -> ci16_le | 44 | Lots of silence, Inst Freq jumps from 0 to 10k Hz | |



// Contact Finder private Google Sheets sync backend (Token-Free & Bulletproof).
//
// SETUP:
// 1. Create a PRIVATE Google Sheet. Do not publish it.
// 2. Create a tab named Contacts.
// 3. Extensions -> Apps Script -> replace script with this file.
// 4. Click Deploy -> New deployment -> Web app:
//    - Execute as: Me
//    - Who has access: Anyone
// 5. Copy Web App URL and paste it into Android -> Cloud Sync Settings.

const SHEET_NAME = 'Contacts';
const HEADERS = ['ID','NAME','PHONE','DETAILS','SAVED','UPDATED_AT','DELETED','DEVICE_ID'];

function sheet_() {
  const ss = SpreadsheetApp.getActive();
  let sh = ss.getSheetByName(SHEET_NAME);
  if (!sh) sh = ss.insertSheet(SHEET_NAME);
  if (sh.getLastRow() === 0) sh.getRange(1,1,1,HEADERS.length).setValues([HEADERS]);
  return sh;
}
function json_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }
function bool_(v) { return String(v).toLowerCase() === 'true' || String(v) === '1'; }

function doGet(e) {
  try {
    const sh = sheet_();
    const last = sh.getLastRow();
    if (last <= 1) return json_({ok:true,rows:[]});
    const values = sh.getRange(2,1,last-1,HEADERS.length).getValues();
    const rows = [];
    values.forEach(r => {
      if (!r[0]) return;
      rows.push({
        id:String(r[0]), name:String(r[1] || ''), phone:String(r[2] || ''), details:String(r[3] || ''),
        saved:bool_(r[4]), updated_at:String(r[5] || ''), deleted:bool_(r[6]), device_id:String(r[7] || '')
      });
    });
    return json_({ok:true,rows:rows});
  } catch(err) { return json_({ok:false,error:String(err)}); }
}

function doPost(e) {
  try {
    const body = JSON.parse((e.postData && e.postData.contents) || '{}');
    if (body.action !== 'upsert') return json_({ok:false,error:'Unsupported action'});

    const incoming = Array.isArray(body.contacts) ? body.contacts : [];
    const sh = sheet_();
    const last = sh.getLastRow();
    const values = last > 1 ? sh.getRange(2,1,last-1,HEADERS.length).getValues() : [];
    const rowById = {};
    values.forEach((r,i) => { if(r[0]) rowById[String(r[0])] = i+2; });

    let inserted=0, updated=0, ignored=0;
    incoming.forEach(c => {
      if (!c || !c.id) { ignored++; return; }
      const id=String(c.id), row=rowById[id], newTime=String(c.updated_at || '');
      const data=[id,String(c.name || ''),String(c.phone || ''),String(c.details || ''),c.saved?'1':'0',newTime,c.deleted?'1':'0',String(c.device_id || '')];
      if(row) {
        const oldTime=String(sh.getRange(row,6).getValue() || '');
        if(newTime >= oldTime) { sh.getRange(row,1,1,HEADERS.length).setValues([data]); updated++; }
        else ignored++;
      } else {
        sh.appendRow(data); inserted++;
      }
    });
    return json_({ok:true,inserted:inserted,updated:updated,ignored:ignored});
  } catch(err) { return json_({ok:false,error:String(err)}); }
}

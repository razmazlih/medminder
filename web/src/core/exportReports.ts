import { jsPDF } from 'jspdf';

import arialHebrewTtfDataUrl from '@/assets/fonts/arial-hebrew.ttf?inline';
import { doseEventEffectiveLocalDate, doseEventEffectiveLocalTime } from '@/domain/doseEventTime';
import type { CloudDeletionNotice, DoseEventRecord } from '@/domain/models';

export type ReportMetadata = {
  fromDate?: string;
  toDate?: string;
  medicationNameFilter?: string;
};

export type ReportExportOptions = {
  metadata?: ReportMetadata;
};

type ResolvedReportMetadata = {
  fromDate?: string;
  toDate?: string;
  medicationNameFilter?: string;
};

const PAGE_MARGIN_X = 40;
const PAGE_MARGIN_TOP = 36;
const PAGE_MARGIN_BOTTOM = 36;
const PAGE_WIDTH = 515;
const PAGE_HEADER_HEIGHT = 56;
const PAGE_FOOTER_HEIGHT = 24;
const LINE_HEIGHT = 16;
const PDF_FONT_NAME = 'ArialHebrew';

let isHebrewFontRegistered = false;

function getBase64FromDataUrl(dataUrl: string): string {
  const separatorIndex = dataUrl.indexOf(',');
  if (separatorIndex === -1) return dataUrl;
  return dataUrl.slice(separatorIndex + 1);
}

function registerHebrewFont(doc: jsPDF): void {
  if (isHebrewFontRegistered) return;
  const base64 = getBase64FromDataUrl(arialHebrewTtfDataUrl);
  doc.addFileToVFS('arial-hebrew.ttf', base64);
  doc.addFont('arial-hebrew.ttf', PDF_FONT_NAME, 'normal');
  doc.addFont('arial-hebrew.ttf', PDF_FONT_NAME, 'bold');
  isHebrewFontRegistered = true;
}

function setPdfFont(doc: jsPDF, style: 'normal' | 'bold'): void {
  doc.setFont(PDF_FONT_NAME, style);
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function isDirectMetadataValue(value: ReportExportOptions | ReportMetadata | undefined): value is ReportMetadata {
  if (!value) {
    return false;
  }

  return 'fromDate' in value || 'toDate' in value || 'medicationNameFilter' in value;
}

function resolveMetadata(options?: ReportExportOptions | ReportMetadata): ResolvedReportMetadata {
  if (!options) {
    return {};
  }

  if (isDirectMetadataValue(options)) {
    return options;
  }

  return options.metadata ?? {};
}

function sanitizeFilenamePart(value: string | undefined): string {
  if (!value) {
    return 'all';
  }

  const cleaned = value.trim().replace(/[^a-zA-Z0-9_-]+/g, '_').replace(/^_+|_+$/g, '');
  return cleaned.length > 0 ? cleaned : 'all';
}

function buildReportFilename(extension: 'csv' | 'pdf', metadata: ResolvedReportMetadata): string {
  if (!metadata.fromDate || !metadata.toDate) {
    return `history_report.${extension}`;
  }

  const from = sanitizeFilenamePart(metadata.fromDate);
  const to = sanitizeFilenamePart(metadata.toDate);

  return `history_report_${from}_${to}.${extension}`;
}

function formatTimestamp(date: Date): string {
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = String(date.getFullYear());
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${day}/${month}/${year} ${hours}:${minutes}`;
}

function csvEscape(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

function appendCsvRow(lines: string[], cells: Array<string | number | null | undefined>): void {
  lines.push(cells.map((cell) => csvEscape(String(cell ?? ''))).join(','));
}

function createPdfDocument(): jsPDF {
  const doc = new jsPDF({
    orientation: 'p',
    unit: 'pt',
    format: 'a4',
  });

  registerHebrewFont(doc);
  setPdfFont(doc, 'normal');
  doc.setLanguage('he');
  doc.setR2L(true);
  return doc;
}

function ensurePageSpace(doc: jsPDF, y: number, requiredHeight: number): number {
  const pageHeight = doc.internal.pageSize.getHeight();
  if (y + requiredHeight <= pageHeight - PAGE_MARGIN_BOTTOM - PAGE_FOOTER_HEIGHT) {
    return y;
  }

  doc.addPage();
  return PAGE_MARGIN_TOP + PAGE_HEADER_HEIGHT + 8;
}

function getSummaryLines(events: DoseEventRecord[], notices: CloudDeletionNotice[]): string[] {
  return [
    `סה"כ אירועים: ${events.length}`,
    `סה"כ אירועי מחיקה מהענן: ${notices.length}`,
  ];
}

function toHebrewStatus(status: DoseEventRecord['status']): string {
  if (status === 'TAKEN') return 'נלקח';
  if (status === 'MISSED') return 'הוחמץ';
  return 'ממתין';
}

function toHebrewSource(event: DoseEventRecord): string {
  if (event.actionSource === 'NOTIFICATION') return 'התראה';
  if (event.actionSource === 'SYSTEM') return 'מערכת';
  if (event.recordSource === 'web') return 'ווב';
  if (event.recordSource === 'mobile') return 'אפליקציה';
  if (event.actionSource === 'APP') return 'אפליקציה';
  return 'לא צוין';
}

function formatDateForPdf(localDate: string): string {
  const [year, month, day] = localDate.split('-');
  if (!year || !month || !day) return localDate;
  return `${day}/${month}/${year}`;
}

function drawLtrText(doc: jsPDF, text: string, x: number, y: number, options?: { align?: 'left' | 'center' | 'right' }): void {
  doc.setR2L(false);
  doc.text(text, x, y, options);
  doc.setR2L(true);
}

function formatTimeForPdf(localTime: string): string {
  const [hours, minutes] = localTime.split(':');
  if (!hours || !minutes) return localTime;
  return `${hours.padStart(2, '0')}:${minutes.padStart(2, '0')}`;
}

function formatRangeDate(localDate?: string): string {
  if (!localDate) return 'כל הטווחים';
  return formatDateForPdf(localDate);
}

function splitNoticeDateTime(deletedAtLabel: string): { date: string; time: string } {
  const [datePart = '', timePart = ''] = deletedAtLabel.split(',').map((part) => part.trim());
  const dateMatch = datePart.match(/^(\d{1,2})\.(\d{1,2})\.(\d{4})$/);
  const timeMatch = timePart.match(/^(\d{1,2}):(\d{1,2})$/);

  const normalizedDate = dateMatch
    ? `${dateMatch[1].padStart(2, '0')}/${dateMatch[2].padStart(2, '0')}/${dateMatch[3]}`
    : datePart;
  const normalizedTime = timeMatch
    ? `${timeMatch[1].padStart(2, '0')}:${timeMatch[2].padStart(2, '0')}`
    : timePart;

  return { date: normalizedDate, time: normalizedTime };
}

function drawPageChrome(doc: jsPDF, metadata: ResolvedReportMetadata, generatedAt: string): void {
  const pageHeight = doc.internal.pageSize.getHeight();

  doc.setFillColor(238, 246, 255);
  doc.roundedRect(PAGE_MARGIN_X, PAGE_MARGIN_TOP, PAGE_WIDTH, PAGE_HEADER_HEIGHT, 8, 8, 'F');
  doc.setDrawColor(162, 197, 255);
  doc.roundedRect(PAGE_MARGIN_X, PAGE_MARGIN_TOP, PAGE_WIDTH, PAGE_HEADER_HEIGHT, 8, 8, 'S');

  setPdfFont(doc, 'bold');
  doc.setFontSize(16);
  doc.text('דוח היסטוריית תרופות', PAGE_MARGIN_X + PAGE_WIDTH - 14, PAGE_MARGIN_TOP + 24, { align: 'right' });

  setPdfFont(doc, 'normal');
  doc.setFontSize(10);
  const rangeLabel = `${formatRangeDate(metadata.fromDate)} - ${formatRangeDate(metadata.toDate)}`;
  doc.text('טווח:', PAGE_MARGIN_X + PAGE_WIDTH - 14, PAGE_MARGIN_TOP + 40, { align: 'right' });
  drawLtrText(doc, rangeLabel, PAGE_MARGIN_X + PAGE_WIDTH - 56, PAGE_MARGIN_TOP + 40, { align: 'right' });
  doc.text('הופק:', PAGE_MARGIN_X + 14, PAGE_MARGIN_TOP + 40, { align: 'left' });
  drawLtrText(doc, generatedAt, PAGE_MARGIN_X + 56, PAGE_MARGIN_TOP + 40, { align: 'left' });

  doc.setDrawColor(220, 224, 230);
  doc.line(PAGE_MARGIN_X, pageHeight - PAGE_MARGIN_BOTTOM - PAGE_FOOTER_HEIGHT, PAGE_MARGIN_X + PAGE_WIDTH, pageHeight - PAGE_MARGIN_BOTTOM - PAGE_FOOTER_HEIGHT);
}

function finalizePageNumbers(doc: jsPDF): void {
  const pageCount = doc.getNumberOfPages();
  const pageHeight = doc.internal.pageSize.getHeight();
  for (let page = 1; page <= pageCount; page += 1) {
    doc.setPage(page);
    setPdfFont(doc, 'normal');
    doc.setFontSize(9);
    doc.setTextColor(110, 120, 130);
    doc.text(`עמוד ${page} מתוך ${pageCount}`, PAGE_MARGIN_X + PAGE_WIDTH - 4, pageHeight - PAGE_MARGIN_BOTTOM - 8, { align: 'right' });
  }
  doc.setTextColor(0, 0, 0);
}

function truncateForWidth(doc: jsPDF, text: string, width: number): string {
  if (doc.getTextWidth(text) <= width) return text;
  const ellipsis = '…';
  let current = text;
  while (current.length > 0 && doc.getTextWidth(`${current}${ellipsis}`) > width) {
    current = current.slice(0, -1);
  }
  return `${current}${ellipsis}`;
}

function drawSectionTitle(doc: jsPDF, y: number, title: string): number {
  y = ensurePageSpace(doc, y, 30);
  setPdfFont(doc, 'bold');
  doc.setFontSize(13);
  doc.setTextColor(35, 49, 66);
  doc.text(title, PAGE_MARGIN_X + PAGE_WIDTH - 2, y, { align: 'right' });
  y += 10;
  doc.setDrawColor(207, 216, 228);
  doc.line(PAGE_MARGIN_X, y, PAGE_MARGIN_X + PAGE_WIDTH, y);
  doc.setTextColor(0, 0, 0);
  return y + 14;
}

function drawSummaryCards(doc: jsPDF, y: number, events: DoseEventRecord[], notices: CloudDeletionNotice[], metadata: ResolvedReportMetadata): number {
  const cardHeight = 56;
  y = ensurePageSpace(doc, y, cardHeight + 8);

  const cardGap = 10;
  const cardWidth = (PAGE_WIDTH - cardGap) / 2;

  doc.setFillColor(248, 250, 255);
  doc.setDrawColor(226, 233, 245);
  doc.roundedRect(PAGE_MARGIN_X, y, cardWidth, cardHeight, 6, 6, 'FD');
  doc.roundedRect(PAGE_MARGIN_X + cardWidth + cardGap, y, cardWidth, cardHeight, 6, 6, 'FD');

  setPdfFont(doc, 'bold');
  doc.setFontSize(11);
  doc.text('אירועים שנכללו', PAGE_MARGIN_X + cardWidth - 12, y + 18, { align: 'right' });
  doc.text('מחיקות ענן שנכללו', PAGE_MARGIN_X + PAGE_WIDTH - 12, y + 18, { align: 'right' });

  doc.setFontSize(20);
  doc.text(String(events.length), PAGE_MARGIN_X + cardWidth - 12, y + 44, { align: 'right' });
  doc.text(String(notices.length), PAGE_MARGIN_X + PAGE_WIDTH - 12, y + 44, { align: 'right' });

  y += cardHeight + 12;
  y = ensurePageSpace(doc, y, 24);
  setPdfFont(doc, 'normal');
  doc.setFontSize(10);
  doc.text(`סינון תרופה: ${metadata.medicationNameFilter ?? 'ללא סינון'}`, PAGE_MARGIN_X + PAGE_WIDTH - 2, y, { align: 'right' });
  return y + 16;
}

function drawEventsTableHeader(doc: jsPDF, y: number): number {
  const rowHeight = 22;
  doc.setFillColor(231, 240, 255);
  doc.rect(PAGE_MARGIN_X, y - 14, PAGE_WIDTH, rowHeight, 'F');
  setPdfFont(doc, 'bold');
  doc.setFontSize(10);

  const right = PAGE_MARGIN_X + PAGE_WIDTH;
  doc.text('מקור', right - 6, y, { align: 'right' });
  doc.text('סטטוס', right - 92, y, { align: 'right' });
  doc.text('תרופה', right - 176, y, { align: 'right' });
  doc.text('תאריך', right - 352, y, { align: 'right' });
  doc.text('שעה', right - 442, y, { align: 'right' });

  return y + 14;
}

function drawEventRow(doc: jsPDF, y: number, event: DoseEventRecord, rowIndex: number): number {
  const rowHeight = 20;
  if (rowIndex % 2 === 0) {
    doc.setFillColor(249, 251, 255);
    doc.rect(PAGE_MARGIN_X, y - 12, PAGE_WIDTH, rowHeight, 'F');
  }

  const right = PAGE_MARGIN_X + PAGE_WIDTH;
  setPdfFont(doc, 'normal');
  doc.setFontSize(10);
  doc.text(truncateForWidth(doc, toHebrewSource(event), 70), right - 6, y, { align: 'right' });
  doc.text(truncateForWidth(doc, toHebrewStatus(event.status), 70), right - 92, y, { align: 'right' });
  doc.text(truncateForWidth(doc, event.medicationName, 160), right - 176, y, { align: 'right' });
  drawLtrText(doc, formatDateForPdf(doseEventEffectiveLocalDate(event)), right - 352, y, { align: 'right' });
  drawLtrText(doc, formatTimeForPdf(doseEventEffectiveLocalTime(event)), right - 442, y, { align: 'right' });

  doc.setDrawColor(238, 241, 246);
  doc.line(PAGE_MARGIN_X, y + 8, PAGE_MARGIN_X + PAGE_WIDTH, y + 8);
  return y + LINE_HEIGHT;
}

function drawNoticesTableHeader(doc: jsPDF, y: number): number {
  const rowHeight = 22;
  doc.setFillColor(255, 239, 239);
  doc.rect(PAGE_MARGIN_X, y - 14, PAGE_WIDTH, rowHeight, 'F');
  setPdfFont(doc, 'bold');
  doc.setFontSize(10);

  const right = PAGE_MARGIN_X + PAGE_WIDTH;
  doc.text('פרטים', right - 6, y, { align: 'right' });
  doc.text('סוג רשומה', right - 280, y, { align: 'right' });
  doc.text('תאריך ושעה', right - 410, y, { align: 'right' });

  return y + 14;
}

function drawNoticeRow(doc: jsPDF, y: number, notice: CloudDeletionNotice, rowIndex: number): number {
  const rowHeight = 20;
  if (rowIndex % 2 === 0) {
    doc.setFillColor(255, 250, 250);
    doc.rect(PAGE_MARGIN_X, y - 12, PAGE_WIDTH, rowHeight, 'F');
  }

  const right = PAGE_MARGIN_X + PAGE_WIDTH;
  setPdfFont(doc, 'normal');
  doc.setFontSize(10);
  doc.text(truncateForWidth(doc, `נמחק: ${notice.medicationName}`, 250), right - 6, y, { align: 'right' });
  doc.text(truncateForWidth(doc, notice.recordTypeLabel, 120), right - 280, y, { align: 'right' });
  const { date, time } = splitNoticeDateTime(notice.deletedAtLabel);
  drawLtrText(doc, truncateForWidth(doc, `${date} ${time}`.trim(), 130), right - 410, y, { align: 'right' });

  doc.setDrawColor(246, 232, 232);
  doc.line(PAGE_MARGIN_X, y + 8, PAGE_MARGIN_X + PAGE_WIDTH, y + 8);
  return y + LINE_HEIGHT;
}

export function exportHistoryCsv(
  events: DoseEventRecord[],
  notices: CloudDeletionNotice[],
  options?: ReportExportOptions | ReportMetadata
): void {
  const metadata = resolveMetadata(options);
  const generatedAt = formatTimestamp(new Date());
  const lines: string[] = [];
  const hasMetadata = Boolean(metadata.fromDate || metadata.toDate || metadata.medicationNameFilter);

  if (hasMetadata) {
    appendCsvRow(lines, ['דוח היסטוריית תרופות']);
    appendCsvRow(lines, ['נוצר בתאריך', generatedAt]);
    appendCsvRow(lines, ['טווח מתאריך', formatRangeDate(metadata.fromDate)]);
    appendCsvRow(lines, ['טווח עד תאריך', formatRangeDate(metadata.toDate)]);
    appendCsvRow(lines, ['סינון תרופה', metadata.medicationNameFilter ?? '']);
    lines.push('');
    appendCsvRow(lines, ['תאריך', 'שעה', 'תרופה', 'סטטוס', 'מקור']);

    events.forEach((event) => {
      appendCsvRow(lines, [
        formatDateForPdf(doseEventEffectiveLocalDate(event)),
        formatTimeForPdf(doseEventEffectiveLocalTime(event)),
        event.medicationName,
        toHebrewStatus(event.status),
        toHebrewSource(event),
      ]);
    });

    if (notices.length > 0) {
      lines.push('');
      appendCsvRow(lines, ['אירועי מחיקה מהענן']);
      appendCsvRow(lines, ['תאריך', 'שעה', 'פרטים']);
      notices.forEach((notice) => {
        const { date, time } = splitNoticeDateTime(notice.deletedAtLabel);
        appendCsvRow(lines, [
          date ?? '',
          time ?? '',
          `נמחק מהענן: ${notice.recordTypeLabel} / ${notice.medicationName}`,
        ]);
      });
    }
  } else {
    lines.push('תאריך,שעה,תרופה,סטטוס,מקור');

    events.forEach((event) => {
      lines.push([
        formatDateForPdf(doseEventEffectiveLocalDate(event)),
        formatTimeForPdf(doseEventEffectiveLocalTime(event)),
        event.medicationName,
        toHebrewStatus(event.status),
        toHebrewSource(event),
      ].join(','));
    });

    if (notices.length > 0) {
      lines.push('');
      lines.push('אירועי מחיקה מהענן');
      lines.push('תאריך,שעה,פרטים');
      notices.forEach((notice) => {
        const { date, time } = splitNoticeDateTime(notice.deletedAtLabel);
        lines.push([
          date ?? '',
          time ?? '',
          `נמחק מהענן: ${notice.recordTypeLabel} / ${notice.medicationName}`,
        ].join(','));
      });
    }
  }

  triggerDownload(new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' }), buildReportFilename('csv', metadata));
}

export function exportHistoryPdf(
  events: DoseEventRecord[],
  notices: CloudDeletionNotice[],
  options?: ReportExportOptions | ReportMetadata
): void {
  const metadata = resolveMetadata(options);
  const generatedAt = formatTimestamp(new Date());
  const doc = createPdfDocument();
  drawPageChrome(doc, metadata, generatedAt);

  let y = PAGE_MARGIN_TOP + PAGE_HEADER_HEIGHT + 16;
  y = drawSummaryCards(doc, y, events, notices, metadata);

  y = drawSectionTitle(doc, y, 'אירועי נטילה');
  y = ensurePageSpace(doc, y, 28);
  y = drawEventsTableHeader(doc, y);
  events.forEach((event, index) => {
    y = ensurePageSpace(doc, y, 22);
    if (y === PAGE_MARGIN_TOP + PAGE_HEADER_HEIGHT + 8) {
      drawPageChrome(doc, metadata, generatedAt);
      y = drawSectionTitle(doc, y + 8, 'אירועי נטילה');
      y = drawEventsTableHeader(doc, y);
    }
    y = drawEventRow(doc, y, event, index);
  });

  if (notices.length > 0) {
    y += 8;
    y = drawSectionTitle(doc, y, 'אירועי מחיקה מהענן');
    y = ensurePageSpace(doc, y, 28);
    y = drawNoticesTableHeader(doc, y);
    notices.forEach((notice, index) => {
      y = ensurePageSpace(doc, y, 22);
      if (y === PAGE_MARGIN_TOP + PAGE_HEADER_HEIGHT + 8) {
        drawPageChrome(doc, metadata, generatedAt);
        y = drawSectionTitle(doc, y + 8, 'אירועי מחיקה מהענן');
        y = drawNoticesTableHeader(doc, y);
      }
      y = drawNoticeRow(doc, y, notice, index);
    });
  }

  const summaryLines = getSummaryLines(events, notices);
  y += 10;
  y = ensurePageSpace(doc, y, 26);
  setPdfFont(doc, 'bold');
  doc.setFontSize(10);
  doc.text(summaryLines.join(' | '), PAGE_MARGIN_X + PAGE_WIDTH - 2, y, { align: 'right' });

  finalizePageNumbers(doc);
  triggerDownload(doc.output('blob'), buildReportFilename('pdf', metadata));
}

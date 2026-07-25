# Compact Upload File Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the upload page denser and keep the desktop asset-file panel exactly as tall as the main scrollable content area, with no vertical whitespace and an internally scrolling file queue.

**Architecture:** Keep the existing upload component and business state intact. Make `UploadScrollArea` the desktop grid and size container, then size its sticky asset-file panel to `100cqh`. This binds the panel height to the actual content viewport without hard-coded pixels or JavaScript measurement, while the existing `980px` breakpoint restores natural height for narrow screens.

**Tech Stack:** React 18, TypeScript, Ant Design, styled-components, Vite, in-app browser verification.

---

### Task 1: Establish the failing layout contract

**Files:**
- Inspect: `frontend/src/features/upload/UploadPage.tsx:80-278`
- Verify: `frontend/src/features/upload/UploadPage.tsx:640-685`

- [ ] **Step 1: Start the frontend development server**

Run:

```bash
cd frontend
rtk pnpm dev --host 127.0.0.1
```

Expected: Vite serves `http://127.0.0.1:5173/upload`.

- [ ] **Step 2: Verify the current page fails the new desktop contract**

At a desktop viewport, locate the `section` containing the exact text `资产文件` and read its bounding box and computed layout:

```js
const panel = [...document.querySelectorAll('section')]
  .find((element) => element.textContent?.includes('资产文件'))

({
  height: panel?.getBoundingClientRect().height,
  display: panel ? getComputedStyle(panel).display : null,
})
```

Expected before implementation: FAIL because the panel starts below the content area's top edge and ends above its bottom edge.

### Task 2: Implement the compact content-height panel

**Files:**
- Modify: `frontend/src/features/upload/UploadPage.tsx:80-278`
- Modify: `frontend/src/features/upload/UploadPage.tsx:640-685`

- [ ] **Step 1: Tighten page-level and metadata spacing**

Adjust the existing styled components without changing their structure:

```tsx
const Header = styled.div`
  min-height: 38px;
  margin-bottom: 8px;
`

const StepNote = styled.div`
  gap: 6px;
  padding: 4px 8px;
`

const IntakeSteps = styled(Steps)`
  margin: 0 0 8px;
  padding: 6px 12px;
`

const MetadataPanel = styled(Section)`
  padding: 12px 14px 14px;

  .ant-form-item {
    margin-bottom: 12px;
  }

  .ant-form-item-label {
    padding-bottom: 4px;
  }
`
```

Also reduce section-header and form-section vertical gaps by `2px` while retaining the current colors, typography hierarchy, borders, and radius.

- [ ] **Step 2: Make the asset-file panel fill the content viewport**

Make `UploadScrollArea` the grid and size container, remove the intermediate `Workspace` wrapper, and update `UploadPanel`, `Dropzone`, `QueueSummary`, and `FilesTable`:

```tsx
const UploadScrollArea = styled.div`
  container-type: size;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 10px;
  align-items: start;
  min-height: 0;
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 0 4px 0 0;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`

const UploadPanel = styled(Section)`
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  align-self: start;
  height: 100cqh;
  max-height: 100cqh;
  min-height: 0;
  padding: 10px;

  > .ant-form-item {
    flex: 0 0 auto;
    margin-bottom: 8px;
  }

  > .ant-alert {
    flex: 0 0 auto;
    padding: 6px 8px;
  }

  @media (max-width: 980px) {
    position: static;
    height: auto;
    max-height: none;
    min-height: 0;
  }
`

const Dropzone = styled(Dragger)`
  && .ant-upload-drag {
    height: 88px;
    min-height: 88px;
  }

  && .ant-upload-drag .ant-upload-btn {
    height: 86px;
    padding: 6px 12px;
  }

  &&& .ant-upload-drag p.ant-upload-drag-icon {
    margin: 0 0 2px;
    line-height: 1;
  }

  &&& .ant-upload-drag p.ant-upload-drag-icon .anticon {
    font-size: 24px;
  }

  &&& .ant-upload-drag p.ant-upload-text {
    margin: 0 0 1px;
    font-size: 13px;
    line-height: 17px;
  }

  &&& .ant-upload-drag p.ant-upload-hint {
    margin: 0;
    font-size: 11px;
    line-height: 15px;
  }
`

const QueueSummary = styled.div`
  flex: 0 0 auto;
  margin-top: 8px;
  padding-top: 8px;
`

const FilesTable = styled.div`
  flex: 1 1 auto;
  min-height: 0;
  margin: 8px -6px;
  overflow: hidden;
`
```

- [ ] **Step 3: Bound file-row scrolling inside the remaining panel space**

Change the existing table scroll setting:

```tsx
<Table
  rowKey="uid"
  columns={columns}
  dataSource={fileList}
  pagination={false}
  size="small"
  scroll={{ y: 90 }}
  locale={{ emptyText: '选择文件后显示处理队列' }}
/>
```

Do not change upload props, form values, validation, grouping, draft saving, or submission callbacks.

- [ ] **Step 4: Run frontend static checks**

Run:

```bash
cd frontend
rtk pnpm lint
rtk pnpm typecheck
```

Expected: both commands exit `0`.

### Task 3: Verify desktop and responsive behavior

**Files:**
- Verify: `frontend/src/features/upload/UploadPage.tsx`

- [ ] **Step 1: Verify the desktop layout contract passes**

At `1366x768`, verify:

```js
const panel = [...document.querySelectorAll('section')]
  .find((element) => element.textContent?.includes('资产文件'))
const content = panel?.parentElement
const submit = [...document.querySelectorAll('button')]
  .find((element) => element.textContent?.includes('提交待整理'))

({
  panelHeight: panel?.getBoundingClientRect().height,
  contentHeight: content?.getBoundingClientRect().height,
  topDelta: panel && content
    ? panel.getBoundingClientRect().top - content.getBoundingClientRect().top
    : null,
  bottomDelta: panel && content
    ? content.getBoundingClientRect().bottom - panel.getBoundingClientRect().bottom
    : null,
  panelDisplay: panel ? getComputedStyle(panel).display : null,
  submitBottom: submit?.getBoundingClientRect().bottom,
  viewportHeight: window.innerHeight,
  horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
})
```

Expected: `panelHeight` equals `contentHeight`, both edge deltas are zero apart from subpixel rounding, `panelDisplay` is `flex`, the submit button remains inside the viewport, and `horizontalOverflow` is `false`. Repeat after scrolling the content area and confirm both edges remain aligned.

- [ ] **Step 2: Verify narrow-screen fallback**

At `970px` wide (below the `980px` breakpoint and above the application's global `960px` minimum width), verify that the asset-file panel uses natural height, the content area is one column, and its controls are not clipped.

- [ ] **Step 3: Capture browser evidence**

Capture desktop and narrow-screen screenshots showing the content-height panel, internal file table region, visible action bar, and responsive single-column fallback.

- [ ] **Step 4: Run the production build and diff checks**

Run:

```bash
cd frontend
rtk pnpm build
cd ..
git diff --check
```

Expected: build succeeds and `git diff --check` prints no errors.

- [ ] **Step 5: Commit only the implementation and plan**

```bash
git add frontend/src/features/upload/UploadPage.tsx docs/superpowers/plans/2026-07-25-compact-upload-file-panel.md
git commit -m "feat: compact upload file workspace"
```

Do not stage or modify unrelated working-tree files, including `CONTEXT.md`.

import React, { useRef, useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import './MonacoEditor.css';

function MonacoEditorWrapper({ code }) {
  const [editorHeight, setEditorHeight] = useState('auto');

  const handleEditorDidMount = () => {
    // Calculate height based on content
    const lineCount = code.split('\n').length;
    const lineHeight = 19;
    const maxHeight = 600;
    const minHeight = 40;
    const calculatedHeight = Math.min(maxHeight, Math.max(minHeight, lineCount * lineHeight + 40));
    setEditorHeight(`${calculatedHeight}px`);
  };

  const options = {
    minimap: { enabled: false },
    lineNumbers: 'on',
    scrollBeyondLastLine: true,
    readOnly: false,
    automaticLayout: true,
    fontSize: 14,
    lineHeight: 19,
    padding: { top: 10, bottom: 10 },
    folding: true,
    renderLineHighlight: 'none',
    wordWrap: 'on',
  };

  return (
    <div className="monaco-editor-wrapper">
      <Editor
        height={editorHeight}
        defaultLanguage="java"
        value={code}
        options={options}
        onMount={handleEditorDidMount}
        theme="vs-light"
        loading={<div className="editor-loading">Loading editor...</div>}
      />
    </div>
  );
}

export default MonacoEditorWrapper;
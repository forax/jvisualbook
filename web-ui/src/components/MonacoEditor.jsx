import React, { useState } from 'react';
import Editor, { loader } from '@monaco-editor/react';
import './MonacoEditor.css';

loader.config({
  paths: { vs: '/vs' },
});

function MonacoEditorWrapper({ code, onChange }) {
  const [editorHeight, setEditorHeight] = useState('auto');

  const handleEditorDidMount = () => {
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
        onChange={onChange}
        theme="vs-light"
        loading={<div className="editor-loading">Loading editor...</div>}
      />
    </div>
  );
}

export default MonacoEditorWrapper;
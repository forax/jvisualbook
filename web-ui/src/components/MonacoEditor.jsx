import React, { useState, useRef, useCallback } from 'react';
import Editor, { loader } from '@monaco-editor/react';
import './MonacoEditor.css';

loader.config({
  paths: { vs: '/vs' },
});

function MonacoEditorWrapper({ code, onChange }) {
  const [editorHeight, setEditorHeight] = useState('auto');
  const [manualHeight, setManualHeight] = useState(null);
  const wrapperRef = useRef(null);
  const isDragging = useRef(false);
  const startY = useRef(0);
  const startHeight = useRef(0);

  const handleEditorDidMount = () => {
    if (manualHeight !== null) return;
    const lineCount = code.split('\n').length;
    const lineHeight = 19;
    const maxHeight = 600;
    const minHeight = 40;
    const calculated = Math.min(maxHeight, Math.max(minHeight, lineCount * lineHeight + 40));
    setEditorHeight(`${calculated}px`);
  };

  const onMouseDown = useCallback((e) => {
    e.preventDefault();
    isDragging.current = true;
    startY.current = e.clientY;
    const el = wrapperRef.current;
    startHeight.current = el ? el.getBoundingClientRect().height : 40;

    const onMouseMove = (e) => {
      if (!isDragging.current) return;
      const delta = e.clientY - startY.current;
      const next = Math.max(40, startHeight.current + delta);
      setManualHeight(next);
      setEditorHeight(`${next}px`);
    };

    const onMouseUp = () => {
      isDragging.current = false;
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
  }, []);

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
    <div className="monaco-editor-wrapper" ref={wrapperRef}>
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
      <div className="editor-resize-handle" onMouseDown={onMouseDown}>
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
          <circle cx="8" cy="8" r="1.2" fill="currentColor" opacity="0.7"/>
          <circle cx="4.5" cy="8" r="1.2" fill="currentColor" opacity="0.7"/>
          <circle cx="8" cy="4.5" r="1.2" fill="currentColor" opacity="0.7"/>
        </svg>
      </div>
    </div>
  );
}

export default MonacoEditorWrapper;
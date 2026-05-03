import React, { useRef, useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import './MonacoEditor.css';

function MonacoEditorWrapper({ code, startLine, showLineHighlight = false, defaultHeight }) {
  const editorRef = useRef(null);
  const decorationsRef = useRef([]);
  const [editorHeight, setEditorHeight] = useState(defaultHeight || 'auto');

  const handleEditorDidMount = (editor, monaco) => {
    editorRef.current = { editor, monaco };

    if (!defaultHeight) {
      // Calculate height based on content
      const lineCount = code.split('\n').length;
      const lineHeight = 19;
      const maxHeight = 600;
      const minHeight = 200;
      const calculatedHeight = Math.min(maxHeight, Math.max(minHeight, lineCount * lineHeight + 40));
      setEditorHeight(`${calculatedHeight}px`);
    }

    if (showLineHighlight) {
      highlightCodeBlock(editor, monaco, startLine, code);
    }
  };

  const highlightCodeBlock = (editor, monaco, startLine, code) => {
    const lineCount = code.split('\n').length;
    const endLine = startLine + lineCount - 1;

    // Create decorations for the current block
    const decorations = [
      {
        range: new monaco.Range(startLine, 1, endLine, 1),
        options: {
          isWholeLine: true,
          className: 'active-code-block',
        }
      }
    ];

    decorationsRef.current = editor.deltaDecorations(decorationsRef.current, decorations);

    // Reveal the line in the editor
    editor.revealLineInCenter(startLine);
  };

  useEffect(() => {
    if (editorRef.current && showLineHighlight) {
      const { editor, monaco } = editorRef.current;
      highlightCodeBlock(editor, monaco, startLine, code);
    }
  }, [code, startLine, showLineHighlight]);

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
    <div className={`monaco-editor-wrapper ${showLineHighlight ? 'highlighted-block' : ''}`}>
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
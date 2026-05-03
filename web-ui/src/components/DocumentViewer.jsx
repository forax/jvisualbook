import React, { useState, useEffect, useCallback } from 'react';
import { fetchChapterDocument } from '../services/api';
import MonacoEditorWrapper from './MonacoEditor';
import './DocumentViewer.css';

function DocumentViewer({ chapterName }) {
  const [document, setDocument] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showFullCode, setShowFullCode] = useState(false);

  useEffect(() => {
    loadDocument();
  }, [chapterName]);

  const loadDocument = async () => {
    try {
      setLoading(true);
      setError(null);
      const doc = await fetchChapterDocument(chapterName);
      setDocument(doc);
    } catch (err) {
      setError('Failed to load document');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const getAllCodeBlocks = useCallback(() => {
    if (!document || !document.sections) return [];

    const codeBlocks = [];
    document.sections.forEach(section => {
      if (section.contents) {
        section.contents.forEach(content => {
          if (content.kind === "CODE") {
            codeBlocks.push(content.text);
          }
        });
      }
    });
    return codeBlocks;
  }, [document]);

  const getCodeStartLine = useCallback((document, contentIndex) => {
    let lineCount = 1;
    let currentCodeIndex = 0;

    document.sections.forEach(section => {
      if (section.contents) {
        section.contents.forEach(content => {
          if (content.kind === "CODE") {
            if (currentCodeIndex < contentIndex) {
              lineCount += content.text.split('\n').length;
              currentCodeIndex++;
            }
          }
        });
      }
    });

    return lineCount;
  }, []);

  const renderContent = (content, contentIndex) => {
    if (!content) return null;

    if (content.kind === "TEXT") {
      return (
        <div key={contentIndex} className="text-content"
             dangerouslySetInnerHTML={{ __html: formatText(content.text) }} />
      );
    }
    if (content.kind === "CODE") {
      const startLine = getCodeStartLine(document, contentIndex);
      return (
        <MonacoEditorWrapper
          key={contentIndex}
          code={content.text}
          startLine={startLine}
          showLineHighlight={true}
        />
      );
    }
  };

  const formatText = (text) => {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br/>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>');
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>Loading document...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="error-container">
        <p>{error}</p>
        <button onClick={loadDocument}>Retry</button>
      </div>
    );
  }

  if (!document || !document.sections) {
    return <div className="error-container">No document found</div>;
  }

  const allCode = getAllCodeBlocks().join('\n\n// ---\n\n');

  return (
    <div className="document-viewer">
      <div className="document-toolbar">
        <h2 className="chapter-title">Chapter: {chapterName}</h2>
        <button
          className="toggle-code-btn"
          onClick={() => setShowFullCode(!showFullCode)}
        >
          {showFullCode ? 'Hide Full Code' : 'Show Full Code'}
        </button>
      </div>

      <div className="document-content">
        {document.sections.map((section, sectionIndex) => (
          <div key={sectionIndex} className="section">
            <h3 className="section-title">{section.title}</h3>
            <div className="section-contents">
              {section.contents?.map((content, contentIndex) =>
                renderContent(content, contentIndex)
              )}
            </div>
          </div>
        ))}
      </div>

      {showFullCode && allCode && (
        <div className="full-code-view">
          <h3>Complete Code</h3>
          <MonacoEditorWrapper
            code={allCode}
            startLine={1}
            showLineHighlight={false}
            defaultHeight="500px"
          />
        </div>
      )}
    </div>
  );
}

export default DocumentViewer;
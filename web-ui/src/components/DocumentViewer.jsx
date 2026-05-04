import React, { useState, useEffect, useCallback, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import { fetchChapterDocument, postCode } from '../services/api';
import MonacoEditorWrapper from './MonacoEditor';
import './DocumentViewer.css';

function DocumentViewer({ chapterName }) {
  const [document, setDocument] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const isExecutionUpdate = useRef(false);

  useEffect(() => {
    loadDocument();
  }, [chapterName]);

  useEffect(() => {
    if (!document) return;
    if (isExecutionUpdate.current) {
      isExecutionUpdate.current = false;
      return;
    }
    const timer = setTimeout(() => {
      runCode();
    }, 1000);
    return () => clearTimeout(timer);
  }, [document]);

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

  const mergeDocument = (document, execution) => {
    let id = 0;
    return {
      sections: document.sections.map(section => {
        const newContents = [];
        section.contents.forEach(content => {
          if (content.kind === "OUTPUT") {
            return; // skip old output
          }
          newContents.push(content);
          if (content.kind === "CODE") {
            var evaluation = execution.evaluations[id++];
            newContents.push({ kind: "OUTPUT", status: evaluation.status, text: evaluation.text });
          }
        });
        return { ...section, contents: newContents };
      })
    };
  };

  const runCode = async () => {
    const codeBlocks = getAllCodeBlocks();
    const code = {
      snippets: codeBlocks.map(text => ({ code: text }))
    };
    const execution = await postCode(code);
    // runCode calls setDocument that calls runCode again, we need a flag to stop
    isExecutionUpdate.current = true;
    setDocument(doc => mergeDocument(doc, execution));
  };

  const handleCodeChange = useCallback((sectionIndex, contentIndex, newValue) => {
    setDocument(doc => ({
      sections: doc.sections.map((section, si) => {
        if (si !== sectionIndex) return section;
        return {
          ...section,
          contents: section.contents.map((content, ci) => {
            if (ci !== contentIndex) return content;
            return { ...content, text: newValue };
          })
        };
      })
    }));
  }, []);

  const renderContent = (content, sectionIndex, contentIndex) => {
    if (!content) return null;

    if (content.kind === "TEXT") {
      return (
        <ReactMarkdown key={contentIndex} className="text-content">
          {content.text}
        </ReactMarkdown>
      );
    }
    if (content.kind === "CODE") {
      return (
        <MonacoEditorWrapper
          key={contentIndex}
          code={content.text}
          onChange={val => handleCodeChange(sectionIndex, contentIndex, val)}
        />
      );
    }
    if (content.kind === "OUTPUT") {
      const status = content.status === "ERROR" ? "status-error" : "";
      return (
        <pre key={contentIndex} className={`text-output ${status}`}>{content.text}</pre>
      );
    }
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

  return (
    <div className="document-viewer">
      <div className="document-toolbar">
        <h2 className="chapter-title">Chapter: {chapterName}</h2>
        <button className="toggle-code-btn" onClick={runCode}>
          Run
        </button>
      </div>

      <div className="document-content">
        {document.sections.map((section, sectionIndex) => (
          <div key={sectionIndex} className="section">
            <ReactMarkdown className="section-title">
              {section.title}
            </ReactMarkdown>
           <br/>
            <div className="section-contents">
              {section.contents?.map((content, contentIndex) =>
                renderContent(content, sectionIndex, contentIndex)
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default DocumentViewer;
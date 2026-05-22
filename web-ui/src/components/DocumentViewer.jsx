import React, { useState, useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import { fetchChapterDocument, postCode } from '../services/api';
import MonacoEditorWrapper from './MonacoEditor';
import SlideViewer from './SlideViewer';
import { printSlidesAsPDF } from './PrintSlides';
import './DocumentViewer.css';

function assignUUID(doc) {
  return {
    sections: doc.sections.map(section => ({
      ...section,
      contents: section.contents.map(content => ({
        ...content,
        id: crypto.randomUUID()
      }))
    }))
  };
}

function getCodeBlocks(doc) {
  return doc.sections.flatMap(section =>
    section.contents
      .filter(content => content.kind === "CODE")
      .map(content => content.text)
  );
}

function mergeDocument(doc, execution) {
  let count = 0;
  return {
    sections: doc.sections.map(section => {
      const newContents = [];
      section.contents.forEach(content => {
        if (content.kind === "OUTPUT") {
          return; // Skip old output
        }
        newContents.push(content);
        if (content.kind === "CODE") {
          const evaluation = execution.evaluations[count++];
          newContents.push({
            kind: "OUTPUT",
            text: evaluation.text,
            id: content.id + "-output",
            status: evaluation.status,
          });
        }
      });
      return { ...section, contents: newContents };
    })
  };
}

function DocumentViewer({ chapterName }) {
  const [loadedDocument, setLoadedDocument] = useState(null);
  const [displayDocument, setDisplayDocument] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [slideMode, setSlideMode] = useState(false);
  const codeAbortController = useRef(null);

  useEffect(() => {
    loadDocument();
  }, [chapterName]);

  useEffect(() => {
    if (!loadedDocument) return;
    const timer = setTimeout(async () => {
      await runCode(loadedDocument);
    }, 500);
    return () => clearTimeout(timer);
  }, [loadedDocument]);

  const loadDocument = async () => {
    setLoading(true);
    setError(null);
    setDisplayDocument(null);
    try {
      const doc = await fetchChapterDocument(chapterName);
      setLoadedDocument(assignUUID(doc));
    } catch (err) {
      setError('Failed to load document');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const runCode = async (doc) => {
    codeAbortController.current?.abort();
    const controller = new AbortController();
    codeAbortController.current = controller;

    const codeBlocks = getCodeBlocks(doc);
    const program = {
      snippets: codeBlocks.map(text => ({ code: text }))
    };
    try {
      const execution = await postCode(program, controller.signal);
      setDisplayDocument(mergeDocument(doc, execution));
    } catch (err) {
      if (err.name === 'AbortError') return;  // postCode() is aborted
      setError('Failed to run document');
      console.error(err);
    }
  };

  const handleCodeChange = (contentId, newValue) => {
    codeAbortController.current?.abort();
    setLoadedDocument(doc => ({
      sections: doc.sections.map(section => ({
        ...section,
        contents: section.contents.map(content => {
          if (content.id !== contentId) return content;
          return { ...content, text: newValue };
        })
      }))
    }));
  };

  const renderContent = content => {
    if (content.kind === "TEXT") {
      return (
        <ReactMarkdown key={content.id} className="text-content">
          {content.text}
        </ReactMarkdown>
      );
    }
    if (content.kind === "CODE") {
      return (
        <div key={content.id}>
          <pre className="editor-text">{content.text}</pre>
          <MonacoEditorWrapper
            code={content.text}
            onChange={val => handleCodeChange(content.id, val)}
          />
        </div>
      );
    }
    if (content.kind === "OUTPUT") {
      const text = content.text;
      const status = content.status === "ERROR" ? "status-error" : "";
      return text && (
        <pre key={content.id} className={`text-output ${status}`}>{text}</pre>
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

  const docToRender = displayDocument ?? loadedDocument;
  if (!docToRender?.sections) {
    return <div className="error-container">No document found</div>;
  }

  if (slideMode) {
    return (
      <SlideViewer
        doc={docToRender}
        onExit={() => setSlideMode(false)}
        renderContent={renderContent}
      />
    );
  }

  return (
    <div className="document-viewer">
      <div className="document-toolbar">
        <h2 className="chapter-title">Chapter: {chapterName}</h2>
        <div className="toolbar-actions">
          <button className="toggle-code-btn" onClick={() => setSlideMode(true)}>
            Slide Mode
          </button>
          <button className="toggle-code-btn" onClick={() => printSlidesAsPDF(docToRender)}>
            Print Slides
          </button>
          <button className="toggle-code-btn" onClick={loadDocument}>
            Reload
          </button>
        </div>
      </div>

      <div className="document-content">
        {docToRender.sections.map((section, sectionIndex) => (
          <div key={sectionIndex} className="section">
            <ReactMarkdown className="section-title">
              {section.title}
            </ReactMarkdown>
            <br/>
            <div className="section-contents">
              {section.contents.map(content =>
                renderContent(content)
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default DocumentViewer;
import React, { useState, useEffect, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import { fetchChapterDocument, postCode } from '../services/api';
import MonacoEditorWrapper from './MonacoEditor';
import './DocumentViewer.css';

function assignUUID(doc) {
  doc.sections.forEach(section => {
    section.contents.forEach(content => {
      content.id = crypto.randomUUID();
    })
  })
}

function getCodeBlocks(doc) {
  if (!doc?.sections) return [];
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
          newContents.push({   // The order of the fields is important!
            kind: "OUTPUT",
            text: evaluation.text,
            id: crypto.randomUUID(),
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

  useEffect(() => {
    loadDocument();
  }, [chapterName]);

  useEffect(() => {
    if (!loadedDocument) return;
    const timer = setTimeout(() => {
      runCode(loadedDocument);
    }, 1000);
    return () => clearTimeout(timer);
  }, [loadedDocument]);

  const loadDocument = async () => {
    try {
      setLoading(true);
      setError(null);
      setDisplayDocument(null);
      const doc = await fetchChapterDocument(chapterName);
      assignUUID(doc);
      setLoadedDocument(doc);
    } catch (err) {
      setError('Failed to load document');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const runCode = async (doc) => {
    const codeBlocks = getCodeBlocks(doc);
    const code = {
      snippets: codeBlocks.map(text => ({ code: text }))
    };
    const execution = await postCode(code);
    setDisplayDocument(mergeDocument(doc, execution));
  };

  const handleCodeChange = useCallback((contentId, newValue) => {
    setLoadedDocument(doc => ({
      sections: doc.sections.map(section => {
        return {
          ...section,
          contents: section.contents.map(content => {
            if (content.id !== contentId) return content;
            return { ...content, text: newValue };
          })
        };
      })
    }));
  }, []);

  const renderContent = content => {
    if (!content) return null;

    if (content.kind === "TEXT") {
      return (
        <ReactMarkdown key={content.id} className="text-content">
          {content.text}
        </ReactMarkdown>
      );
    }
    if (content.kind === "CODE") {
      return (
        <MonacoEditorWrapper
          key={content.id}
          code={content.text}
          onChange={val => handleCodeChange(content.id, val)}
        />
      );
    }
    if (content.kind === "OUTPUT") {
      const status = content.status === "ERROR" ? "status-error" : "";
      return (
        <pre key={content.id} className={`text-output ${status}`}>{content.text}</pre>
      );
    }

    console.warn('Unknown content kind:', content.kind);
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

  return (
    <div className="document-viewer">
      <div className="document-toolbar">
        <h2 className="chapter-title">Chapter: {chapterName}</h2>
        <button className="toggle-code-btn" onClick={() => runCode(loadedDocument)}>
          Reload
        </button>
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
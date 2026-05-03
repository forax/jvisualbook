import React, { useState, useEffect } from 'react';
import DocumentViewer from './components/DocumentViewer';
import { fetchChapters } from './services/api';

function App() {
  const [chapters, setChapters] = useState([]);
  const [selectedChapter, setSelectedChapter] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadChapters();
  }, []);

  const loadChapters = async () => {
    try {
      setLoading(true);
      const chaptersData = await fetchChapters();
      setChapters(chaptersData);
      if (chaptersData.length > 0) {
        setSelectedChapter(chaptersData[0]);
      }
    } catch (err) {
      setError('Failed to load chapters');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>Loading chapters...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="error-container">
        <p>{error}</p>
        <button onClick={loadChapters}>Retry</button>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>JVisualBook</h1>
        {chapters.length > 0 && (
          <nav>
            <select
              value={selectedChapter?.name || ''}
              onChange={(e) => {
                const chapter = chapters.find(c => c.name === e.target.value);
                setSelectedChapter(chapter);
              }}
            >
              {chapters.map(chapter => (
                <option key={chapter.name} value={chapter.name}>
                  {chapter.name}
                </option>
              ))}
            </select>
          </nav>
        )}
      </header>
      <main>
        {selectedChapter && (
          <DocumentViewer
            key={selectedChapter.name}
            chapterName={selectedChapter.name}
          />
        )}
        {!selectedChapter && chapters.length === 0 && (
          <div className="no-chapters">
            <p>No chapters available</p>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
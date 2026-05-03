const API_BASE_URL = '/api';

export async function fetchChapters() {
  const response = await fetch(`${API_BASE_URL}/chapter`, {
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    }
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch chapters: ${response.statusText}`);
  }
  return await response.json();
}

export async function fetchChapterDocument(chapterName) {
  const response = await fetch(`${API_BASE_URL}/chapter/${encodeURIComponent(chapterName)}`, {
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    }
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch document for chapter: ${chapterName}`);
  }
  return await response.json();
}
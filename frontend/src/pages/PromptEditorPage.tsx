import { useParams } from 'react-router-dom';

// Placeholder — real prompt editor + version-history panel is a later session.
export function PromptEditorPage() {
  const { id } = useParams<{ id: string }>();
  return (
    <main className="p-6">
      <h1 className="text-xl font-semibold text-gray-900">Prompt Editor</h1>
      <p className="mt-2 text-sm text-gray-600">Prompt #{id}</p>
    </main>
  );
}

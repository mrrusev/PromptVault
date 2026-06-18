import { useSearchParams } from 'react-router-dom';

// The sidebar (mounted by AppShell) owns the collections list and its actions.
// This page only reflects the currently selected collection from the URL.
export function CollectionsPage() {
  const [searchParams] = useSearchParams();
  const selected = searchParams.get('selected');

  if (!selected) {
    return (
      <section>
        <h1 className="text-xl font-semibold text-gray-900">Collections</h1>
        <p className="mt-4 text-sm text-gray-500">Select a collection, or create one</p>
      </section>
    );
  }

  return (
    <section>
      <h1 className="text-xl font-semibold text-gray-900">Collection #{selected}</h1>
    </section>
  );
}

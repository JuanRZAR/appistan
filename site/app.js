fetch('release.json', { cache: 'no-store' }).then(response => {
  if (!response.ok) throw new Error('Release unavailable');
  return response.json();
}).then(release => {
  if (!/^\d+\.\d+\.\d+$/.test(release.version) || !/^AppIstan-\d+\.\d+\.\d+\.apk$/.test(release.artifact)) return;
  document.querySelector('#download').href = `downloads/${release.artifact}`;
  document.querySelector('#version').textContent = `v${release.version}`;
  if (Number.isFinite(release.sizeBytes) && release.sizeBytes > 0) document.querySelector('#size').textContent = `· ${(release.sizeBytes / 1048576).toFixed(1)} MB`;
}).catch(() => { /* The stable APK link remains usable without JavaScript or metadata. */ });

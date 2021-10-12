import rollupProject from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'LichessSense',
    input: 'src/main.ts',
    output: 'sense',
  },
});

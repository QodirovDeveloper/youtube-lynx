// Android'dagi Native Module'lar (android/app/src/main/java/com/example/ytlynx/)
declare let NativeModules: {
  StorageModule?: {
    setItem(key: string, value: string): void;
    getItem(key: string, callback: (value: string | null) => void): void;
  };
  PlayerModule?: {
    command(action: string, value: number): void;
    load(url: string): void;
  };
};

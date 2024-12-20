## <img src="https://waterplayer.ru/assets/icon.svg" align="center" width="60pt"> WaterPlayer API
Публичный API для хорошой работы WaterPlayer

## Функции
### Публичные плейлисты
Вы можете публиковать плейлисты и делиться с друзьями

### Информация о треке
Для работы Discord RPC используется дополнительная информация, например икокна автора или же самого трека, если у вас играет локальная музыка

## Пути:
| Метод       | Адрес                                          | Примечание                                                          | Функция                                 |
|-------------|------------------------------------------------|---------------------------------------------------------------------|-----------------------------------------|
| GET, DELETE | `wplayer.ru/id`, `wplayer.ru/playlist/id`      | Для метода Delete требуется авторизация                             | Получение/Удаление плейлиста            |
| POST        | `wplayer.ru/upload`                            | Для официального API требуется лицензия игры                        | Публикация плейлиста                    |
| GET         | `wplayer.ru/search?query=query`                | Аргумент Query необязателен                                         | Поиск плейлистов                        |
| GET         | `wplayer.ru/info?author=author&album=album`    | Аргумент Album необязателен, но требуется Author во всех сценариях  | Инфомация о треке                       |
| GET         | `wplayer.ru/artwork?author=author&album=album` | ^                                                                   | Переадресация на изображения по запросу |
| GET         | `wplayer.ru/release`                           |                                                                     | Информация о сборке                     |
| GET         | `wplayer.ru/public_config`                     |                                                                     | Публичный конфиг для мода               |
| GET         | `wplayer.ru/ping`                              |                                                                     | Pong! [для u.kelcu.ru]                  |
| GET         | `wplayer.ru/user`                              | Требуется авторизация                                               | Получение информации о пользователе     |
| GET         | `wplayer.ru/verify`                            | Требуется лицензия игры, при включенном VERIFY в public_config.json | Проверка лицензии                       |

(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('HtmlSanitizer', function($sanitize) {
            var entityMap = {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': '&quot;',
                "'": '&#39;',
                "/": '&#x2F;'
            };

            this.sanitize = function(str) {
                return $sanitize(String(str).replace(/[&<>"'\/]/g, function(s) {
                    return entityMap[s];
                }));
            };
        });
})();
